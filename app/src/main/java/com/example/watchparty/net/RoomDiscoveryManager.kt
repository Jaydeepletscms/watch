package com.example.watchparty.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import com.example.watchparty.util.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Dual-Mode Local Wi-Fi / Hotspot Auto Room Finder
 * Combines Android NSD (mDNS `_watchparty._tcp.`) AND UDP LAN Broadcast (port 8081)
 * so rooms appear automatically on the Join Screen with host name, room code, participant count,
 * and password-protected lock status.
 */
class RoomDiscoveryManager(
    context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "RoomDiscovery"
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val serviceType = "_watchparty._tcp."
    private val udpDiscoveryPort = 8081

    private val discoveredMap = ConcurrentHashMap<String, DiscoveredRoom>()
    private val _discoveredRooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = _discoveredRooms.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var broadcastJob: Job? = null
    private var udpListenJob: Job? = null
    private var pruneJob: Job? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Called by Host when a room is active so nearby phones automatically discover it.
     */
    fun startAdvertisingRoom(
        roomCode: String,
        hostName: String,
        port: Int = 8080,
        hasPassword: Boolean,
        getParticipantCount: () -> Int
    ) {
        stopAdvertisingRoom()

        // 1. Register NSD (mDNS) service
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "WatchParty_${roomCode}_${hostName.take(10).replace(" ", "_")}_${if (hasPassword) "1" else "0"}"
                serviceType = this@RoomDiscoveryManager.serviceType
                setPort(port)
            }
            val regListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    Log.d(tag, "NSD registered: ${info?.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
            }
            registrationListener = regListener
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)
        } catch (e: Exception) {
            Log.w(tag, "NSD register failed: ${e.message}")
        }

        // 2. Start UDP LAN Broadcast Beacon every 1.2s
        broadcastJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                while (isActive) {
                    val ip = Net.getLocalIpAddress(appContext)
                    val beacon = DiscoveredRoom(
                        roomCode = roomCode,
                        hostName = hostName,
                        hostIp = ip,
                        port = port,
                        hasPassword = hasPassword,
                        participantCount = getParticipantCount(),
                        maxParticipants = Protocol.MAX_ROOM_PARTICIPANTS,
                        lastSeenMs = SystemClock.elapsedRealtime()
                    )
                    val bytes = Protocol.json.encodeToString(beacon).toByteArray(Charsets.UTF_8)

                    // Send to 255.255.255.255, subnet .255, and localhost 127.0.0.1
                    val targets = mutableSetOf("255.255.255.255", "127.0.0.1")
                    if (ip.contains(".")) {
                        targets.add(ip.substringBeforeLast(".") + ".255")
                    }
                    for (targetIp in targets) {
                        try {
                            val packet = DatagramPacket(
                                bytes,
                                bytes.size,
                                InetAddress.getByName(targetIp),
                                udpDiscoveryPort
                            )
                            socket.send(packet)
                        } catch (_: Exception) {
                        }
                    }
                    delay(1200L)
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun stopAdvertisingRoom() {
        broadcastJob?.cancel()
        broadcastJob = null
        registrationListener?.let { listener ->
            try {
                nsdManager?.unregisterService(listener)
            } catch (_: Exception) {
            }
        }
        registrationListener = null
    }

    /**
     * Called on JoinScreen to automatically discover nearby rooms on the same Wi-Fi or Hotspot.
     */
    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        discoveredMap.clear()
        _discoveredRooms.value = emptyList()

        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("WatchPartyDiscoveryLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }

        // 1. Start NSD Discovery
        try {
            val discListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String?) {}
                override fun onServiceFound(service: NsdServiceInfo?) {
                    val name = service?.serviceName ?: return
                    if (!name.startsWith("WatchParty_")) return
                    try {
                        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo?) {
                                val hostIp = resolved?.host?.hostAddress ?: return
                                val parts = resolved.serviceName.split("_")
                                val code = parts.getOrNull(1) ?: "4F2"
                                val hostName = parts.getOrNull(2) ?: "Host"
                                val hasPwd = parts.getOrNull(3) == "1"
                                upsertRoom(
                                    DiscoveredRoom(
                                        roomCode = code,
                                        hostName = hostName,
                                        hostIp = hostIp,
                                        port = resolved.port,
                                        hasPassword = hasPwd,
                                        participantCount = 1,
                                        maxParticipants = Protocol.MAX_ROOM_PARTICIPANTS,
                                        lastSeenMs = SystemClock.elapsedRealtime()
                                    )
                                )
                            }
                        })
                    } catch (_: Exception) {
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            }
            discoveryListener = discListener
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discListener)
        } catch (_: Exception) {
        }

        // 2. Start UDP Broadcast Listener on port 8081
        udpListenJob?.cancel()
        udpListenJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(udpDiscoveryPort))
                }
                val buf = ByteArray(2048)
                while (isActive) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val parsed = try {
                        Protocol.json.decodeFromString<DiscoveredRoom>(raw)
                    } catch (_: Exception) {
                        null
                    }
                    if (parsed != null) {
                        val senderIp = packet.address?.hostAddress
                        val effectiveIp = if (parsed.hostIp == "127.0.0.1" && !senderIp.isNullOrBlank()) {
                            senderIp
                        } else {
                            parsed.hostIp
                        }
                        upsertRoom(
                            parsed.copy(
                                hostIp = effectiveIp,
                                lastSeenMs = SystemClock.elapsedRealtime()
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

        // 3. Prune stale rooms older than 6 seconds
        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (isActive) {
                delay(2000L)
                val now = SystemClock.elapsedRealtime()
                val removed = discoveredMap.entries.removeIf { (_, room) ->
                    now - room.lastSeenMs > 6500L
                }
                if (removed) {
                    refreshFlow()
                }
            }
        }
    }

    private fun upsertRoom(room: DiscoveredRoom) {
        val key = "${room.hostIp}:${room.roomCode}"
        discoveredMap[key] = room
        refreshFlow()
    }

    private fun refreshFlow() {
        _discoveredRooms.value = discoveredMap.values.sortedBy { it.roomCode }
    }

    fun stopScanning() {
        _isScanning.value = false
        udpListenJob?.cancel()
        udpListenJob = null
        pruneJob?.cancel()
        pruneJob = null
        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        discoveryListener = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    fun stopAll() {
        stopAdvertisingRoom()
        stopScanning()
    }

    fun release() {
        stopAll()
    }
}
