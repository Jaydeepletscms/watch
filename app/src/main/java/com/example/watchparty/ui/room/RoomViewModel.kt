package com.example.watchparty.ui.room

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchparty.media.ChunkStore
import com.example.watchparty.media.Chunker
import com.example.watchparty.media.SoundEffectItem
import com.example.watchparty.media.SoundboardEngine
import com.example.watchparty.media.VideoProbeResult
import com.example.watchparty.media.VoiceEngine
import com.example.watchparty.net.ClientConnection
import com.example.watchparty.net.ConnectionStatus
import com.example.watchparty.net.DiscoveredRoom
import com.example.watchparty.net.HostServer
import com.example.watchparty.net.ParticipantDto
import com.example.watchparty.net.Protocol
import com.example.watchparty.net.RoomDiscoveryManager
import com.example.watchparty.net.WsMessage
import com.example.watchparty.service.HostForegroundService
import com.example.watchparty.sync.SyncEngine
import com.example.watchparty.util.DeviceHealthStatus
import com.example.watchparty.util.Hash
import com.example.watchparty.util.Net
import com.example.watchparty.util.QrJoinPayload
import com.example.watchparty.util.SampleVideoGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.BitSet
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class RoomViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val chunkStore = ChunkStore(appContext)
    val chunker = Chunker(appContext, chunkStore)
    val roomDiscovery = RoomDiscoveryManager(appContext, viewModelScope)
    val soundboardEngine = SoundboardEngine(viewModelScope)

    private val _localIp = MutableStateFlow(Net.getLocalIpAddress(appContext))
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _hasEnoughStorage = MutableStateFlow(Net.hasEnoughStorage(appContext.cacheDir))
    val hasEnoughStorage: StateFlow<Boolean> = _hasEnoughStorage.asStateFlow()

    private val _deviceHealth = MutableStateFlow(Net.getDeviceHealth(appContext))
    val deviceHealth: StateFlow<DeviceHealthStatus> = _deviceHealth.asStateFlow()

    // Host Setup State
    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _videoProbe = MutableStateFlow<VideoProbeResult?>(null)
    val videoProbe: StateFlow<VideoProbeResult?> = _videoProbe.asStateFlow()

    private val _isGeneratingSample = MutableStateFlow(false)
    val isGeneratingSample: StateFlow<Boolean> = _isGeneratingSample.asStateFlow()

    private val _selectedChunkSec = MutableStateFlow(10)
    val selectedChunkSec: StateFlow<Int> = _selectedChunkSec.asStateFlow()

    private val _hostRoomPassword = MutableStateFlow("")
    val hostRoomPassword: StateFlow<String> = _hostRoomPassword.asStateFlow()

    // Room & Lobby State
    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _roomCode = MutableStateFlow("4F2")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _joinHostIp = MutableStateFlow("")
    val joinHostIp: StateFlow<String> = _joinHostIp.asStateFlow()

    private val _joinRoomCode = MutableStateFlow("")
    val joinRoomCode: StateFlow<String> = _joinRoomCode.asStateFlow()

    private val _joinRoomPassword = MutableStateFlow("")
    val joinRoomPassword: StateFlow<String> = _joinRoomPassword.asStateFlow()

    private val _joinError = MutableStateFlow<String?>(null)
    val joinError: StateFlow<String?> = _joinError.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _participants = MutableStateFlow<List<ParticipantDto>>(emptyList())
    val participants: StateFlow<List<ParticipantDto>> = _participants.asStateFlow()

    private val _isRoomLocked = MutableStateFlow(false)
    val isRoomLocked: StateFlow<Boolean> = _isRoomLocked.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val chatMessages: StateFlow<ChatUiMessage>.() -> Unit = {}
    val chatMessagesFlow: StateFlow<List<ChatUiMessage>> = _chatMessages.asStateFlow()

    private val _floatingReactions = MutableStateFlow<List<FloatingReactionItem>>(emptyList())
    val floatingReactions: StateFlow<List<FloatingReactionItem>> = _floatingReactions.asStateFlow()

    // Audio, Auto-Ducking & Playback Speed State
    private val _videoVolume = MutableStateFlow(1.0f)
    val videoVolume: StateFlow<Float> = _videoVolume.asStateFlow()

    private val _isAutoDuckEnabled = MutableStateFlow(true)
    val isAutoDuckEnabled: StateFlow<Boolean> = _isAutoDuckEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var currentLocalUserName: String = "Host"
    private var hostServer: HostServer? = null
    private var clientConnection: ClientConnection? = null
    var syncEngine: SyncEngine? = null
        private set

    val voiceEngine = VoiceEngine(
        scope = viewModelScope,
        onSendVoicePacket = { pcmBase64 ->
            val msg = WsMessage.VoiceData(from = currentLocalUserName, pcmBase64 = pcmBase64)
            if (_isHost.value) {
                hostServer?.broadcastText(msg)
            } else {
                clientConnection?.send(msg)
            }
        },
        onVoiceStateChanged = { isMicOn, isSpeaking ->
            if (_isHost.value) {
                hostServer?.updateHostVoiceState(isMicOn = isMicOn, isSpeaking = isSpeaking)
                hostServer?.broadcastText(
                    WsMessage.VoiceState(
                        from = currentLocalUserName,
                        isMicOn = isMicOn,
                        isSpeaking = isSpeaking
                    )
                )
            } else {
                clientConnection?.send(
                    WsMessage.VoiceState(
                        from = currentLocalUserName,
                        isMicOn = isMicOn,
                        isSpeaking = isSpeaking
                    )
                )
            }
        }
    )

    private val simulatedFriends = mutableListOf<ClientConnection>()
    private val simulatedNames = listOf("Rahul", "Priya", "Amit", "Neha", "Vikram")

    private val reactionTimestamps = ArrayDeque<Long>()
    private val nextMessageId = AtomicLong(1L)
    private var healthMonitorJob: Job? = null

    init {
        chunkStore.clearAll()
        val ip = Net.getLocalIpAddress(appContext)
        _localIp.value = ip
        if (ip != "127.0.0.1" && ip.contains(".")) {
            _joinHostIp.value = ip.substringBeforeLast(".") + "."
        } else {
            _joinHostIp.value = "127.0.0.1"
        }
        startDeviceHealthMonitor()
        observeVoiceAutoDucking()
    }

    private fun observeVoiceAutoDucking() {
        viewModelScope.launch {
            combine(
                voiceEngine.isLocalSpeaking,
                voiceEngine.activeRemoteSpeaker,
                _videoVolume,
                _isAutoDuckEnabled
            ) { localSpeaking, remoteSpeaker, baseVol, autoDuck ->
                val someoneSpeaking = localSpeaking || remoteSpeaker != null
                if (autoDuck && someoneSpeaking) {
                    (baseVol * 0.28f).coerceIn(0.05f, 1f)
                } else {
                    baseVol
                }
            }.collect { effectiveVol ->
                syncEngine?.chunkPlayer?.setVideoVolume(effectiveVol)
            }
        }
    }

    private fun startDeviceHealthMonitor() {
        healthMonitorJob?.cancel()
        healthMonitorJob = viewModelScope.launch {
            while (isActive) {
                _deviceHealth.value = Net.getDeviceHealth(appContext)
                _localIp.value = Net.getLocalIpAddress(appContext)
                _hasEnoughStorage.value = Net.hasEnoughStorage(appContext.cacheDir)
                delay(5000L)
            }
        }
    }

    fun selectVideoUri(uri: Uri) {
        _selectedVideoUri.value = uri
        viewModelScope.launch {
            _videoProbe.value = chunker.inspectVideo(uri)
        }
    }

    fun generateBuiltInSampleVideo() {
        if (_isGeneratingSample.value) return
        _isGeneratingSample.value = true
        viewModelScope.launch {
            try {
                val uri = SampleVideoGenerator.generateSampleMp4(appContext, durationSeconds = 30)
                _selectedVideoUri.value = uri
                _videoProbe.value = chunker.inspectVideo(uri)
            } finally {
                _isGeneratingSample.value = false
            }
        }
    }

    fun setChunkLengthSec(seconds: Int) {
        _selectedChunkSec.value = seconds
    }

    fun updateHostRoomPassword(password: String) {
        _hostRoomPassword.value = password
    }

    fun updateJoinHostIp(ip: String) {
        _joinHostIp.value = ip
        _joinError.value = null
    }

    fun updateJoinRoomCode(code: String) {
        _joinRoomCode.value = code
        _joinError.value = null
    }

    fun updateJoinRoomPassword(password: String) {
        _joinRoomPassword.value = password
        _joinError.value = null
    }

    fun startScanningForRooms() {
        roomDiscovery.startScanning()
    }

    fun stopScanningForRooms() {
        roomDiscovery.stopScanning()
    }

    fun selectDiscoveredRoomAndJoin(
        room: DiscoveredRoom,
        password: String,
        userName: String,
        onJoinedLobby: () -> Unit,
        onRoomStarted: () -> Unit
    ) {
        _joinHostIp.value = room.hostIp
        _joinRoomCode.value = room.roomCode
        _joinRoomPassword.value = password
        _joinError.value = null
        joinPartyAsParticipant(
            userName = userName,
            onJoinedLobby = onJoinedLobby,
            onRoomStarted = onRoomStarted
        )
    }

    fun onQrScanned(payload: QrJoinPayload) {
        _joinHostIp.value = payload.ip
        _joinRoomCode.value = payload.room
        if (payload.pwd.isNotBlank()) {
            _joinRoomPassword.value = payload.pwd
        }
        _joinError.value = null
    }

    fun createHostRoom(hostUserName: String, onRoomCreated: () -> Unit) {
        cleanupActiveSession()
        currentLocalUserName = hostUserName.trim().ifBlank { "Host" }
        val code = Net.generateRoomCode()
        val cleanPassword = _hostRoomPassword.value.trim()
        _roomCode.value = code
        _isHost.value = true

        val server = HostServer(
            hostName = currentLocalUserName,
            roomCode = code,
            roomPassword = cleanPassword,
            port = Net.DEFAULT_PORT
        )
        server.startServer()
        hostServer = server

        // Advertise room on WiFi / Hotspot via NSD + UDP Broadcast for Auto Room Finder
        roomDiscovery.startAdvertisingRoom(
            roomCode = code,
            hostName = currentLocalUserName,
            port = Net.DEFAULT_PORT,
            hasPassword = cleanPassword.isNotBlank(),
            getParticipantCount = { server.participants.value.size }
        )

        val engine = SyncEngine(
            context = appContext,
            scope = viewModelScope,
            isHost = true,
            chunkStore = chunkStore,
            chunker = chunker,
            hostServer = server,
            clientConnection = null
        )
        syncEngine = engine

        HostForegroundService.start(
            context = appContext,
            roomCode = code,
            hostIp = _localIp.value
        )

        viewModelScope.launch {
            server.participants.collect { list ->
                _participants.value = list
            }
        }
        viewModelScope.launch {
            server.isLocked.collect { locked ->
                _isRoomLocked.value = locked
            }
        }
        viewModelScope.launch {
            server.clientEvents.collect { evt ->
                when (val msg = evt.message) {
                    is WsMessage.Join -> {
                        if (msg.isReconnect) {
                            appendSystemChat("${msg.name} reconnected & synced ✓")
                        } else {
                            appendSystemChat("${msg.name} joined the watch party")
                        }
                    }
                    is WsMessage.Chat -> {
                        appendUserChat(
                            from = msg.from,
                            text = msg.text,
                            ts = msg.ts,
                            isMe = false,
                            replyTo = msg.replyTo
                        )
                    }
                    is WsMessage.Reaction -> {
                        spawnFloatingReaction(from = msg.from, emoji = msg.emoji)
                    }
                    is WsMessage.SoundEffect -> {
                        soundboardEngine.playSoundEffect(msg.soundId)
                        spawnFloatingReaction(
                            from = msg.from,
                            emoji = "${msg.emoji} ${msg.label}"
                        )
                    }
                    is WsMessage.VoiceData -> {
                        voiceEngine.onIncomingVoicePacket(senderName = msg.from, pcmBase64 = msg.pcmBase64)
                    }
                    else -> {}
                }
            }
        }

        onRoomCreated()
    }

    fun addSimulatedFriendInLobby() {
        val server = hostServer ?: return
        if (server.participants.value.size >= Protocol.MAX_ROOM_PARTICIPANTS) return
        val friendIndex = simulatedFriends.size
        val friendName = simulatedNames.getOrElse(friendIndex) { "Friend${friendIndex + 1}" }

        // If room was locked, unlock temporarily so simulated friend can join
        if (server.isLocked.value) {
            server.setLocked(false)
        }

        val botClient = ClientConnection()
        simulatedFriends.add(botClient)
        botClient.connect(
            hostIp = "127.0.0.1",
            port = Net.DEFAULT_PORT,
            roomCode = _roomCode.value,
            userName = friendName,
            password = _hostRoomPassword.value.trim(),
            autoReconnect = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            val botDir = File(appContext.cacheDir, "bot_${friendIndex}_chunks").apply { mkdirs() }
            var expectedMeta: WsMessage.ChunkMeta? = null
            var receivedFrames = BitSet()
            var partFile: File? = null
            var lastPct = 0

            launch {
                botClient.textMessages.collect { msg ->
                    when (msg) {
                        is WsMessage.Welcome -> {
                            repeat(4) {
                                botClient.send(WsMessage.TimeReq(t0 = SystemClock.elapsedRealtime()))
                                delay(150L)
                            }
                        }
                        is WsMessage.ChunkMeta -> {
                            expectedMeta = msg
                            receivedFrames = BitSet(msg.totalFrames)
                            lastPct = 0
                            val pf = File(botDir, "chunk_${msg.chunkNo}.part")
                            if (pf.exists()) pf.delete()
                            partFile = pf
                        }
                        is WsMessage.PlayAt -> {
                            if (friendIndex == 0) {
                                delay(2200L)
                                botClient.send(
                                    WsMessage.Chat(
                                        from = friendName,
                                        text = "Kya scene hai! Video aur voice dono mast chal rahe hain 🔥",
                                        ts = System.currentTimeMillis()
                                    )
                                )
                                delay(900L)
                                botClient.send(WsMessage.Reaction(from = friendName, emoji = "🔥"))
                                botClient.send(WsMessage.VoiceState(from = friendName, isMicOn = true, isSpeaking = true))
                                val chimeBytes = ByteArray(1600)
                                for (i in 0 until 800) {
                                    val s = (sin(2.0 * PI * 523.25 * (i / 16000.0)) * 2200).toInt().toShort()
                                    chimeBytes[i * 2] = (s.toInt() and 0xFF).toByte()
                                    chimeBytes[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                                }
                                val b64 = Base64.encodeToString(chimeBytes, Base64.NO_WRAP)
                                repeat(6) {
                                    botClient.send(WsMessage.VoiceData(from = friendName, pcmBase64 = b64))
                                    delay(50L)
                                }
                                delay(400L)
                                botClient.send(WsMessage.VoiceState(from = friendName, isMicOn = true, isSpeaking = false))
                            } else {
                                delay(1200L)
                                botClient.send(
                                    WsMessage.Chat(
                                        from = friendName,
                                        text = "Main bhi join ho gaya! Exact time par sync ho gaya 🍿",
                                        ts = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }

            launch {
                botClient.binaryFrames.collect { frame ->
                    val meta = expectedMeta ?: return@collect
                    val pf = partFile ?: return@collect
                    if (frame.chunkNo != meta.chunkNo) return@collect

                    RandomAccessFile(pf, "rw").use { raf ->
                        raf.seek(frame.frameIndex.toLong() * Protocol.MAX_FRAME_PAYLOAD_BYTES)
                        raf.write(frame.payload)
                    }
                    receivedFrames.set(frame.frameIndex)
                    val pct = ((receivedFrames.cardinality() * 100) / meta.totalFrames.coerceAtLeast(1)).coerceIn(0, 100)
                    if (pct - lastPct >= 10 || pct == 100) {
                        lastPct = pct
                        botClient.send(WsMessage.Progress(chunkNo = meta.chunkNo, percent = pct))
                    }
                    if (receivedFrames.cardinality() >= meta.totalFrames) {
                        val sha = Hash.sha256(pf)
                        pf.delete()
                        if (sha.equals(meta.sha256, ignoreCase = true)) {
                            botClient.send(WsMessage.Ready(chunkNo = meta.chunkNo))
                        }
                    }
                }
            }
        }
    }

    fun joinPartyAsParticipant(
        userName: String,
        onJoinedLobby: () -> Unit,
        onRoomStarted: () -> Unit
    ) {
        cleanupActiveSession()
        currentLocalUserName = userName.trim().ifBlank { "Guest" }
        _isHost.value = false
        _joinError.value = null
        _isConnecting.value = true

        val client = ClientConnection()
        clientConnection = client
        val engine = SyncEngine(
            context = appContext,
            scope = viewModelScope,
            isHost = false,
            chunkStore = chunkStore,
            chunker = chunker,
            hostServer = null,
            clientConnection = client
        )
        syncEngine = engine

        var navigatedToLobby = false
        var navigatedToRoom = false

        viewModelScope.launch {
            client.participants.collect { list ->
                _participants.value = list
            }
        }

        viewModelScope.launch {
            client.status.collect { status ->
                when (status) {
                    is ConnectionStatus.Connected -> {
                        _isConnecting.value = false
                        _roomCode.value = status.roomName.removePrefix("Room ").trim()
                        if (status.isReconnected) {
                            appendSystemChat("Reconnected to Host & resynced ✓")
                        }
                        if (!navigatedToLobby) {
                            navigatedToLobby = true
                            roomDiscovery.stopScanning()
                            onJoinedLobby()
                        }
                    }
                    is ConnectionStatus.Reconnecting -> {
                        if (status.attempt == 1) {
                            appendSystemChat("WiFi glitch — Auto-reconnecting to Host... 🔄")
                        }
                    }
                    is ConnectionStatus.Rejected -> {
                        _isConnecting.value = false
                        _joinError.value = status.reason
                    }
                    is ConnectionStatus.Error -> {
                        _isConnecting.value = false
                        _joinError.value = status.message
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            client.textMessages.collect { msg ->
                when (msg) {
                    is WsMessage.ChunkMeta -> {
                        if (!navigatedToRoom) {
                            navigatedToRoom = true
                            onRoomStarted()
                        }
                    }
                    is WsMessage.Chat -> {
                        val isFromMe = msg.from.equals(currentLocalUserName, ignoreCase = true)
                        if (!isFromMe) {
                            appendUserChat(
                                from = msg.from,
                                text = msg.text,
                                ts = msg.ts,
                                isMe = false,
                                replyTo = msg.replyTo
                            )
                        }
                    }
                    is WsMessage.Reaction -> {
                        val isFromMe = msg.from.equals(currentLocalUserName, ignoreCase = true)
                        if (!isFromMe) {
                            spawnFloatingReaction(from = msg.from, emoji = msg.emoji)
                        }
                    }
                    is WsMessage.SoundEffect -> {
                        val isFromMe = msg.from.equals(currentLocalUserName, ignoreCase = true)
                        if (!isFromMe) {
                            soundboardEngine.playSoundEffect(msg.soundId)
                            spawnFloatingReaction(
                                from = msg.from,
                                emoji = "${msg.emoji} ${msg.label}"
                            )
                        }
                    }
                    is WsMessage.VoiceData -> {
                        voiceEngine.onIncomingVoicePacket(senderName = msg.from, pcmBase64 = msg.pcmBase64)
                    }
                    is WsMessage.Mute -> {
                        voiceEngine.setMicEnabled(false)
                        appendSystemChat("Host muted your microphone")
                    }
                    else -> {}
                }
            }
        }

        client.connect(
            hostIp = _joinHostIp.value,
            port = Net.DEFAULT_PORT,
            roomCode = _joinRoomCode.value,
            userName = currentLocalUserName,
            password = _joinRoomPassword.value.trim(),
            autoReconnect = true
        )
    }

    fun startHostPartyPlayback(onRoomOpened: () -> Unit) {
        val uri = _selectedVideoUri.value ?: return
        onRoomOpened()
        syncEngine?.startHostPipeline(
            videoUri = uri,
            chunkLengthSec = _selectedChunkSec.value,
            startFromMs = 0L
        )
    }

    fun toggleMic() {
        val next = !voiceEngine.isMicOn.value
        voiceEngine.setMicEnabled(next)
    }

    fun toggleSpeaker() {
        voiceEngine.toggleSpeaker()
    }

    fun setVideoVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _videoVolume.value = clamped
        syncEngine?.chunkPlayer?.setVideoVolume(clamped)
    }

    fun setVoiceVolume(volume: Float) {
        voiceEngine.setVoiceVolume(volume)
    }

    fun toggleAutoDuck() {
        _isAutoDuckEnabled.value = !_isAutoDuckEnabled.value
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        syncEngine?.chunkPlayer?.setPlaybackSpeed(speed)
    }

    fun toggleRoomLock() {
        val server = hostServer ?: return
        server.setLocked(!server.isLocked.value)
    }

    fun kickParticipant(userId: String) {
        hostServer?.kickParticipant(userId)
    }

    fun toggleMuteParticipant(userId: String) {
        val muted = hostServer?.toggleMuteParticipant(userId) ?: return
        appendSystemChat(if (muted) "Participant muted by host" else "Participant unmuted")
    }

    fun toggleHostPlayPause() {
        val engine = syncEngine ?: return
        if (engine.chunkPlayer.isPlaying.value) {
            engine.hostPause()
        } else {
            engine.hostResume()
        }
    }

    fun triggerStartAnyway() {
        syncEngine?.triggerStartAnyway()
    }

    fun seekHostTo(targetPosMs: Long) {
        val uri = _selectedVideoUri.value ?: return
        syncEngine?.hostSeekTo(
            videoUri = uri,
            chunkLengthSec = _selectedChunkSec.value,
            targetPosMs = targetPosMs
        )
    }

    fun sendChatMessage(senderName: String, text: String, replyTo: String? = null) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val now = System.currentTimeMillis()
        val sender = senderName.trim().ifBlank { currentLocalUserName }
        val msg = WsMessage.Chat(
            from = sender,
            text = cleanText,
            ts = now,
            replyTo = replyTo
        )
        appendUserChat(from = sender, text = cleanText, ts = now, isMe = true, replyTo = replyTo)
        if (_isHost.value) {
            hostServer?.broadcastText(msg)
        } else {
            clientConnection?.send(msg)
        }
    }

    fun sendReaction(senderName: String, emoji: String) {
        val now = SystemClock.elapsedRealtime()
        while (reactionTimestamps.isNotEmpty() && now - reactionTimestamps.first() > 1000L) {
            reactionTimestamps.removeFirst()
        }
        if (reactionTimestamps.size >= 3) return
        reactionTimestamps.addLast(now)

        val sender = senderName.trim().ifBlank { currentLocalUserName }
        val msg = WsMessage.Reaction(
            from = sender,
            emoji = emoji
        )
        spawnFloatingReaction(from = sender, emoji = emoji)
        if (_isHost.value) {
            hostServer?.broadcastText(msg)
        } else {
            clientConnection?.send(msg)
        }
    }

    fun sendSoundEffect(senderName: String, item: SoundEffectItem) {
        val now = SystemClock.elapsedRealtime()
        while (reactionTimestamps.isNotEmpty() && now - reactionTimestamps.first() > 1200L) {
            reactionTimestamps.removeFirst()
        }
        if (reactionTimestamps.size >= 3) return
        reactionTimestamps.addLast(now)

        val sender = senderName.trim().ifBlank { currentLocalUserName }
        soundboardEngine.playSoundEffect(item.id)
        spawnFloatingReaction(from = sender, emoji = "${item.emoji} ${item.label}")

        val msg = WsMessage.SoundEffect(
            from = sender,
            soundId = item.id,
            label = item.label,
            emoji = item.emoji
        )
        if (_isHost.value) {
            hostServer?.broadcastText(msg)
        } else {
            clientConnection?.send(msg)
        }
    }

    fun removeFinishedReaction(id: Long) {
        _floatingReactions.value = _floatingReactions.value.filterNot { it.id == id }
    }

    private fun appendUserChat(
        from: String,
        text: String,
        ts: Long,
        isMe: Boolean,
        replyTo: String? = null
    ) {
        val item = ChatUiMessage(
            id = nextMessageId.getAndIncrement(),
            from = from,
            text = text,
            timestamp = ts,
            isMe = isMe,
            isSystem = false,
            replyToText = replyTo
        )
        _chatMessages.value = (_chatMessages.value + item).takeLast(120)
    }

    private fun appendSystemChat(text: String) {
        val item = ChatUiMessage(
            id = nextMessageId.getAndIncrement(),
            from = "System",
            text = text,
            timestamp = System.currentTimeMillis(),
            isMe = false,
            isSystem = true
        )
        _chatMessages.value = (_chatMessages.value + item).takeLast(120)
    }

    private fun spawnFloatingReaction(from: String, emoji: String) {
        val item = FloatingReactionItem(
            id = nextMessageId.getAndIncrement(),
            emoji = emoji,
            sender = from,
            horizontalFraction = Random.nextFloat() * 0.7f + 0.15f
        )
        _floatingReactions.value = (_floatingReactions.value + item).takeLast(18)
    }

    fun cleanupActiveSession() {
        roomDiscovery.stopAll()
        voiceEngine.setMicEnabled(false)
        simulatedFriends.forEach { it.release() }
        simulatedFriends.clear()
        clientConnection?.release()
        clientConnection = null
        hostServer?.stopServer()
        hostServer = null
        syncEngine?.cleanup()
        syncEngine = null
        HostForegroundService.stop(appContext)
        chunkStore.clearAll()
        _chatMessages.value = emptyList()
        _floatingReactions.value = emptyList()
        _isConnecting.value = false
    }

    override fun onCleared() {
        cleanupActiveSession()
        soundboardEngine.release()
        voiceEngine.release()
        super.onCleared()
    }
}
