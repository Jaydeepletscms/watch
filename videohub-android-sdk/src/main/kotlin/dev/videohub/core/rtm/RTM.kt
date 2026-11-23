package dev.videohub.core.rtm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * RTM (Real-Time Messaging) client.
 *
 * Connects to wss://rtms.videohub.dev/ws?token=...
 * Uses plain JSON protocol: {event, payload}
 *
 * Matches the official SDK's RTM API.
 */
class RTM(
    private val scope: CoroutineScope,
) {
    private var socket: WebSocket? = null
    private var connected = false

    private val _messages = MutableSharedFlow<RTMMessage>(extraBufferCapacity = 128)
    val messages: SharedFlow<RTMMessage> = _messages.asSharedFlow()

    /** Moderation API — matches activeRtm?.moderation?.muteUser/kickUser */
    val moderation = Moderation(this)

    /** Chat API */
    val chat = Chat(this)

    /** Presence API */
    val presence = Presence(this)

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun isConnected(): Boolean = connected

    /** Connect to RTM server */
    fun connect(token: String, roomId: String) {
        if (connected) return

        val url = "wss://rtms.videohub.dev/ws?token=$token"
        val request = Request.Builder().url(url).build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                // Send room join
                send("room.join", JSONObject().put("room_id", roomId))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = json.optString("event", "")
                    val payload = json.optJSONObject("payload") ?: JSONObject()
                    scope.launch {
                        _messages.emit(RTMMessage(event, payload))
                    }
                } catch (_: Exception) {}
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
            }
        })
    }

    /** Send a message */
    fun send(event: String, payload: JSONObject) {
        if (!connected) return
        val msg = JSONObject().put("event", event).put("payload", payload)
        socket?.send(msg.toString())
    }

    /** Disconnect */
    fun disconnect() {
        socket?.close(1000, "client closed")
        socket = null
        connected = false
    }
}

data class RTMMessage(val event: String, val payload: JSONObject)

/** Moderation sub-module — matches activeRtm?.moderation?.muteUser/kickUser */
class Moderation(private val rtm: RTM) {
    fun muteUser(roomId: String, targetUserId: String) {
        rtm.send("user.mute", JSONObject()
            .put("room_id", roomId)
            .put("target_user_id", targetUserId))
    }

    fun kickUser(roomId: String, targetUserId: String) {
        rtm.send("user.kick", JSONObject()
            .put("room_id", roomId)
            .put("target_user_id", targetUserId))
    }
}

/** Chat sub-module */
class Chat(private val rtm: RTM) {
    fun send(roomId: String, message: String, messageId: String = java.util.UUID.randomUUID().toString()): String {
        rtm.send("chat.message", JSONObject()
            .put("room_id", roomId)
            .put("message", message)
            .put("message_id", messageId))
        return messageId
    }

    fun typingStart(roomId: String) {
        rtm.send("typing.start", JSONObject().put("room_id", roomId))
    }

    fun typingStop(roomId: String) {
        rtm.send("typing.stop", JSONObject().put("room_id", roomId))
    }

    fun deleteMessage(roomId: String, messageId: String) {
        rtm.send("chat.message.delete", JSONObject()
            .put("room_id", roomId)
            .put("message_id", messageId))
    }
}

/** Presence sub-module */
class Presence(private val rtm: RTM) {
    fun joinRoom(roomId: String) {
        rtm.send("room.join", JSONObject().put("room_id", roomId))
    }

    fun leaveRoom(roomId: String) {
        rtm.send("room.leave", JSONObject().put("room_id", roomId))
    }
}
