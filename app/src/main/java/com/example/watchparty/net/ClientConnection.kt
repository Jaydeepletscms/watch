package com.example.watchparty.net

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data class Connected(
        val userId: String,
        val roomName: String,
        val isReconnected: Boolean = false
    ) : ConnectionStatus()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionStatus()
    data class Rejected(val reason: String) : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class ClientConnection {
    private val tag = "ClientConnection"
    private val maxReconnectAttempts = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep WebSocket open indefinitely
        .pingInterval(12, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private var lastHostIp: String = ""
    private var lastPort: Int = 8080
    private var lastRoomCode: String = ""
    private var lastUserName: String = ""
    private var lastPassword: String = ""

    @Volatile
    private var shouldAutoReconnect: Boolean = false

    @Volatile
    private var hasConnectedOnce: Boolean = false

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _participants = MutableStateFlow<List<ParticipantDto>>(emptyList())
    val participants: StateFlow<List<ParticipantDto>> = _participants.asStateFlow()

    private val _textMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 128)
    val textMessages: SharedFlow<WsMessage> = _textMessages.asSharedFlow()

    private val _binaryFrames = MutableSharedFlow<BinaryChunkFrame>(extraBufferCapacity = 256)
    val binaryFrames: SharedFlow<BinaryChunkFrame> = _binaryFrames.asSharedFlow()

    fun connect(
        hostIp: String,
        port: Int = 8080,
        roomCode: String,
        userName: String,
        password: String = "",
        autoReconnect: Boolean = true
    ) {
        reconnectJob?.cancel()
        closeSocketQuietly()

        lastHostIp = hostIp.trim().removePrefix("ws://").substringBefore(":")
        lastPort = port
        lastRoomCode = roomCode.trim()
        lastUserName = userName.trim()
        lastPassword = password.trim()
        shouldAutoReconnect = autoReconnect
        hasConnectedOnce = false

        _status.value = ConnectionStatus.Connecting
        openSocketAttempt(isReconnectAttempt = false)
    }

    fun retryManualReconnect() {
        if (lastHostIp.isBlank()) return
        reconnectJob?.cancel()
        closeSocketQuietly()
        shouldAutoReconnect = true
        _status.value = ConnectionStatus.Reconnecting(1, maxReconnectAttempts)
        openSocketAttempt(isReconnectAttempt = true)
    }

    private fun openSocketAttempt(isReconnectAttempt: Boolean) {
        val url = "ws://$lastHostIp:$lastPort"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "Connected to $url (reconnect=$isReconnectAttempt), sending join")
                send(
                    WsMessage.Join(
                        name = lastUserName,
                        roomCode = lastRoomCode,
                        password = lastPassword,
                        isReconnect = isReconnectAttempt
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = Protocol.decodeText(text) ?: return
                when (msg) {
                    is WsMessage.Welcome -> {
                        val wasReconnect = hasConnectedOnce || isReconnectAttempt
                        hasConnectedOnce = true
                        reconnectJob?.cancel()
                        _participants.value = msg.participants
                        _status.value = ConnectionStatus.Connected(
                            userId = msg.userId,
                            roomName = msg.roomName,
                            isReconnected = wasReconnect
                        )
                    }
                    is WsMessage.Reject -> {
                        shouldAutoReconnect = false
                        reconnectJob?.cancel()
                        val readable = when (msg.reason) {
                            "full" -> "Room full (Max 6 log allowed)"
                            "locked" -> "Room locked by host 🔒"
                            "invalid_room" -> "Room code match nahi hua"
                            "wrong_password" -> "Galat password! Sahi room password daalein 🔒"
                            else -> "Join rejected: ${msg.reason}"
                        }
                        _status.value = ConnectionStatus.Rejected(readable)
                    }
                    is WsMessage.Participants -> {
                        _participants.value = msg.list
                    }
                    is WsMessage.Kick -> {
                        shouldAutoReconnect = false
                        reconnectJob?.cancel()
                        _status.value = ConnectionStatus.Rejected("Host ne aapko room se remove kar diya")
                        closeSocketQuietly()
                    }
                    is WsMessage.Ended -> {
                        shouldAutoReconnect = false
                        reconnectJob?.cancel()
                    }
                    else -> {}
                }
                _textMessages.tryEmit(msg)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = Protocol.decodeBinaryFrame(bytes.toByteArray()) ?: return
                _binaryFrames.tryEmit(frame)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                if (shouldAutoReconnect && hasConnectedOnce && code != 1000 && code != 1008) {
                    scheduleAutoReconnectLoop()
                } else if (_status.value is ConnectionStatus.Connected) {
                    _status.value = ConnectionStatus.Error("Connection band ho gaya: $reason")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}")
                if (_status.value is ConnectionStatus.Rejected) return

                if (shouldAutoReconnect && hasConnectedOnce) {
                    scheduleAutoReconnectLoop()
                } else if (!isReconnectAttempt) {
                    _status.value = ConnectionStatus.Error(
                        "Host nahi mila (${t.localizedMessage ?: "Check WiFi / IP"})"
                    )
                }
            }
        })
    }

    private fun scheduleAutoReconnectLoop() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            for (attempt in 1..maxReconnectAttempts) {
                if (!isActive || !shouldAutoReconnect) return@launch
                if (_status.value is ConnectionStatus.Connected) return@launch

                _status.value = ConnectionStatus.Reconnecting(
                    attempt = attempt,
                    maxAttempts = maxReconnectAttempts
                )
                closeSocketQuietly()
                openSocketAttempt(isReconnectAttempt = true)

                delay(2200L)
                if (_status.value is ConnectionStatus.Connected) {
                    return@launch
                }
            }
            if (_status.value !is ConnectionStatus.Connected && shouldAutoReconnect) {
                _status.value = ConnectionStatus.Error(
                    "Connection lost. WiFi check karein ya Reconnect dabayein."
                )
            }
        }
    }

    fun send(msg: WsMessage): Boolean {
        val ws = webSocket ?: return false
        return ws.send(Protocol.encodeText(msg))
    }

    fun sendBinary(bytes: ByteArray): Boolean {
        val ws = webSocket ?: return false
        return ws.send(bytes.toByteString())
    }

    private fun closeSocketQuietly() {
        try {
            webSocket?.close(1000, "Closing")
        } catch (_: Exception) {
        }
        webSocket = null
    }

    fun disconnect() {
        shouldAutoReconnect = false
        hasConnectedOnce = false
        reconnectJob?.cancel()
        closeSocketQuietly()
        _status.value = ConnectionStatus.Disconnected
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
