package com.example.watchparty.net

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class IncomingClientEvent(
    val userId: String,
    val message: WsMessage
)

class HostServer(
    val hostName: String,
    val roomCode: String,
    val roomPassword: String = "",
    val port: Int = 8080
) {
    private val tag = "HostServer"
    private val hostUserId = "host_0"

    private val connToUserId = ConcurrentHashMap<WebSocket, String>()
    private val userIdToConn = ConcurrentHashMap<String, WebSocket>()
    private val participantMap = ConcurrentHashMap<String, ParticipantDto>()
    private val mutedUsers = ConcurrentHashMap.newKeySet<String>()

    private val _participants = MutableStateFlow<List<ParticipantDto>>(emptyList())
    val participants: StateFlow<List<ParticipantDto>> = _participants.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _clientEvents = MutableSharedFlow<IncomingClientEvent>(extraBufferCapacity = 256)
    val clientEvents: SharedFlow<IncomingClientEvent> = _clientEvents.asSharedFlow()

    private var wsServer: WebSocketServer? = null

    init {
        participantMap[hostUserId] = ParticipantDto(
            userId = hostUserId,
            name = "$hostName (Host)",
            isHost = true,
            readyChunk = 0,
            downloadPercent = 100
        )
        refreshParticipantList()
    }

    fun startServer() {
        if (_isRunning.value) return
        val server = object : WebSocketServer(InetSocketAddress(port)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
                Log.d(tag, "Client socket opened: ${conn.remoteSocketAddress}")
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
                val uid = connToUserId.remove(conn)
                if (uid != null) {
                    userIdToConn.remove(uid)
                    participantMap.remove(uid)
                    mutedUsers.remove(uid)
                    refreshParticipantList()
                    broadcastParticipants()
                }
            }

            override fun onMessage(conn: WebSocket, message: String?) {
                if (message == null) return
                val decoded = Protocol.decodeText(message) ?: return
                handleClientMessage(conn, decoded)
            }

            override fun onError(conn: WebSocket?, ex: Exception?) {
                Log.e(tag, "WebSocketServer error: ${ex?.message}")
            }

            override fun onStart() {
                Log.d(tag, "HostServer started on port $port")
                _isRunning.value = true
            }
        }.apply {
            isReuseAddr = true
            connectionLostTimeout = 30
        }
        wsServer = server
        try {
            server.start()
        } catch (e: Exception) {
            Log.e(tag, "Failed to start HostServer: ${e.message}")
        }
    }

    private fun handleClientMessage(conn: WebSocket, msg: WsMessage) {
        when (msg) {
            is WsMessage.Join -> {
                val cleanName = msg.name.trim().ifBlank { "Guest" }
                // If reconnecting, clean up any stale entry with the same name first
                if (msg.isReconnect) {
                    val staleEntry = participantMap.entries.firstOrNull {
                        !it.value.isHost && it.value.name.equals(cleanName, ignoreCase = true)
                    }
                    if (staleEntry != null) {
                        val staleUid = staleEntry.key
                        participantMap.remove(staleUid)
                        userIdToConn.remove(staleUid)?.let { oldConn ->
                            connToUserId.remove(oldConn)
                        }
                    }
                }

                if (_isLocked.value && !msg.isReconnect) {
                    sendToSocket(conn, WsMessage.Reject(reason = "locked"))
                    conn.close(1008, "Room locked")
                    return
                }
                if (participantMap.size >= Protocol.MAX_ROOM_PARTICIPANTS) {
                    sendToSocket(conn, WsMessage.Reject(reason = "full"))
                    conn.close(1008, "Room full")
                    return
                }
                if (msg.roomCode.isNotBlank() && !msg.roomCode.equals(roomCode, ignoreCase = true)) {
                    sendToSocket(conn, WsMessage.Reject(reason = "invalid_room"))
                    conn.close(1008, "Invalid room code")
                    return
                }
                if (roomPassword.isNotBlank() && msg.password.trim() != roomPassword.trim()) {
                    sendToSocket(conn, WsMessage.Reject(reason = "wrong_password"))
                    conn.close(1008, "Wrong room password")
                    return
                }

                val newUserId = "user_" + UUID.randomUUID().toString().take(6)
                connToUserId[conn] = newUserId
                userIdToConn[newUserId] = conn
                participantMap[newUserId] = ParticipantDto(
                    userId = newUserId,
                    name = cleanName,
                    isHost = false,
                    readyChunk = 0,
                    downloadPercent = 0
                )
                refreshParticipantList()

                sendToSocket(
                    conn,
                    WsMessage.Welcome(
                        userId = newUserId,
                        roomName = "Room $roomCode",
                        participants = _participants.value
                    )
                )
                broadcastParticipants()
                _clientEvents.tryEmit(IncomingClientEvent(newUserId, msg))
            }

            is WsMessage.TimeReq -> {
                sendToSocket(
                    conn,
                    WsMessage.TimeRes(
                        t0 = msg.t0,
                        hostMs = SystemClock.elapsedRealtime()
                    )
                )
            }

            is WsMessage.Progress -> {
                val uid = connToUserId[conn] ?: return
                participantMap.computeIfPresent(uid) { _, old ->
                    old.copy(downloadPercent = msg.percent.coerceIn(0, 100))
                }
                refreshParticipantList()
                _clientEvents.tryEmit(IncomingClientEvent(uid, msg))
            }

            is WsMessage.Ready -> {
                val uid = connToUserId[conn] ?: return
                participantMap.computeIfPresent(uid) { _, old ->
                    old.copy(readyChunk = msg.chunkNo, downloadPercent = 100)
                }
                refreshParticipantList()
                broadcastParticipants()
                _clientEvents.tryEmit(IncomingClientEvent(uid, msg))
            }

            is WsMessage.ChunkDone, is WsMessage.Resend -> {
                val uid = connToUserId[conn] ?: return
                _clientEvents.tryEmit(IncomingClientEvent(uid, msg))
            }

            is WsMessage.Chat -> {
                val uid = connToUserId[conn] ?: return
                if (mutedUsers.contains(uid)) return
                val senderName = participantMap[uid]?.name ?: msg.from
                val relayed = msg.copy(from = senderName, ts = System.currentTimeMillis())
                broadcastText(relayed)
                _clientEvents.tryEmit(IncomingClientEvent(uid, relayed))
            }

            is WsMessage.Reaction -> {
                val uid = connToUserId[conn] ?: return
                if (mutedUsers.contains(uid)) return
                val senderName = participantMap[uid]?.name ?: msg.from
                val relayed = msg.copy(from = senderName)
                broadcastText(relayed)
                _clientEvents.tryEmit(IncomingClientEvent(uid, relayed))
            }

            is WsMessage.SoundEffect -> {
                val uid = connToUserId[conn] ?: return
                if (mutedUsers.contains(uid)) return
                val senderName = participantMap[uid]?.name ?: msg.from
                val relayed = msg.copy(from = senderName)
                broadcastText(relayed)
                _clientEvents.tryEmit(IncomingClientEvent(uid, relayed))
            }

            is WsMessage.VoiceData -> {
                val uid = connToUserId[conn] ?: return
                if (mutedUsers.contains(uid)) return
                val senderName = participantMap[uid]?.name ?: msg.from
                val relayed = msg.copy(from = senderName)
                // Relay voice audio to all OTHER participants and to the Host
                broadcastTextExcept(uid, relayed)
                _clientEvents.tryEmit(IncomingClientEvent(uid, relayed))
            }

            is WsMessage.VoiceState -> {
                val uid = connToUserId[conn] ?: return
                val isAllowed = !mutedUsers.contains(uid)
                val senderName = participantMap[uid]?.name ?: msg.from
                participantMap.computeIfPresent(uid) { _, old ->
                    old.copy(
                        isMicOn = isAllowed && msg.isMicOn,
                        isSpeaking = isAllowed && msg.isSpeaking
                    )
                }
                refreshParticipantList()
                broadcastParticipants()
                val relayed = msg.copy(
                    from = senderName,
                    isMicOn = isAllowed && msg.isMicOn,
                    isSpeaking = isAllowed && msg.isSpeaking
                )
                broadcastTextExcept(uid, relayed)
                _clientEvents.tryEmit(IncomingClientEvent(uid, relayed))
            }

            is WsMessage.PlayAt,
            is WsMessage.Pause,
            is WsMessage.Resume,
            is WsMessage.ChunkMeta,
            is WsMessage.Waiting,
            is WsMessage.Sync,
            is WsMessage.Welcome,
            is WsMessage.Reject,
            is WsMessage.Participants,
            is WsMessage.TimeRes,
            is WsMessage.Kick,
            is WsMessage.Mute,
            is WsMessage.Seek,
            is WsMessage.Ended -> {
                Log.w(tag, "Ignored privileged message ${msg.type} from participant")
            }
        }
    }

    fun updateHostVoiceState(isMicOn: Boolean, isSpeaking: Boolean) {
        participantMap.computeIfPresent(hostUserId) { _, old ->
            old.copy(isMicOn = isMicOn, isSpeaking = isSpeaking)
        }
        refreshParticipantList()
        broadcastParticipants()
    }

    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    fun kickParticipant(userId: String) {
        if (userId == hostUserId) return
        val conn = userIdToConn.remove(userId)
        if (conn != null) {
            connToUserId.remove(conn)
            sendToSocket(conn, WsMessage.Kick(userId = userId))
            try {
                conn.close(1000, "Kicked by host")
            } catch (_: Exception) {
            }
        }
        participantMap.remove(userId)
        mutedUsers.remove(userId)
        refreshParticipantList()
        broadcastParticipants()
    }

    fun toggleMuteParticipant(userId: String): Boolean {
        if (userId == hostUserId) return false
        return if (mutedUsers.contains(userId)) {
            mutedUsers.remove(userId)
            false
        } else {
            mutedUsers.add(userId)
            participantMap.computeIfPresent(userId) { _, old ->
                old.copy(isMicOn = false, isSpeaking = false)
            }
            refreshParticipantList()
            broadcastParticipants()
            userIdToConn[userId]?.let { sendToSocket(it, WsMessage.Mute(userId = userId)) }
            true
        }
    }

    fun updateHostReadyChunk(chunkNo: Int) {
        participantMap.computeIfPresent(hostUserId) { _, old ->
            old.copy(readyChunk = chunkNo, downloadPercent = 100)
        }
        refreshParticipantList()
        broadcastParticipants()
    }

    fun resetParticipantsDownloadProgress(forChunkNo: Int) {
        participantMap.forEach { (uid, p) ->
            if (!p.isHost && p.readyChunk < forChunkNo) {
                participantMap[uid] = p.copy(downloadPercent = 0)
            }
        }
        refreshParticipantList()
    }

    fun broadcastParticipants() {
        broadcastText(WsMessage.Participants(list = _participants.value))
    }

    fun broadcastText(msg: WsMessage) {
        val raw = Protocol.encodeText(msg)
        userIdToConn.values.forEach { conn ->
            if (conn.isOpen) {
                try {
                    conn.send(raw)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun broadcastTextExcept(excludedUserId: String, msg: WsMessage) {
        val raw = Protocol.encodeText(msg)
        userIdToConn.forEach { (uid, conn) ->
            if (uid != excludedUserId && conn.isOpen) {
                try {
                    conn.send(raw)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun sendTextToUser(userId: String, msg: WsMessage) {
        val conn = userIdToConn[userId] ?: return
        sendToSocket(conn, msg)
    }

    fun broadcastBinaryFrame(frameBytes: ByteArray) {
        userIdToConn.values.forEach { conn ->
            if (conn.isOpen) {
                try {
                    conn.send(frameBytes)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun sendBinaryFrameToUser(userId: String, frameBytes: ByteArray) {
        val conn = userIdToConn[userId] ?: return
        if (conn.isOpen) {
            try {
                conn.send(frameBytes)
            } catch (_: Exception) {
            }
        }
    }

    private fun sendToSocket(conn: WebSocket, msg: WsMessage) {
        if (conn.isOpen) {
            try {
                conn.send(Protocol.encodeText(msg))
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshParticipantList() {
        val ordered = participantMap.values.sortedWith(
            compareByDescending<ParticipantDto> { it.isHost }.thenBy { it.name }
        )
        _participants.value = ordered
    }

    fun stopServer() {
        try {
            broadcastText(WsMessage.Ended())
        } catch (_: Exception) {
        }
        try {
            wsServer?.stop(500)
        } catch (_: Exception) {
        }
        wsServer = null
        connToUserId.clear()
        userIdToConn.clear()
        _isRunning.value = false
    }
}
