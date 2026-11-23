package com.example.watchparty.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Serializable
data class ParticipantDto(
    val userId: String,
    val name: String,
    val isHost: Boolean = false,
    val readyChunk: Int = 0,
    val downloadPercent: Int = 0,
    val isSpeaking: Boolean = false,
    val isMicOn: Boolean = false
)

@Serializable
data class SlowUserDto(
    val name: String,
    val percent: Int
)

@Serializable
data class DiscoveredRoom(
    val roomCode: String,
    val hostName: String,
    val hostIp: String,
    val port: Int = 8080,
    val hasPassword: Boolean = false,
    val participantCount: Int = 1,
    val maxParticipants: Int = 6,
    val lastSeenMs: Long = 0L
)

/**
 * Network Protocol
 * Single WebSocket connection (port 8080).
 * Text frames = JSON control, chat, reaction, soundboard, and voice messages.
 * Binary frames = 8-byte big-endian header + media payload.
 */
sealed class WsMessage {
    abstract val type: String

    @Serializable
    data class Join(
        override val type: String = "join",
        val name: String,
        val roomCode: String = "",
        val password: String = "",
        val isReconnect: Boolean = false
    ) : WsMessage()

    @Serializable
    data class Welcome(
        override val type: String = "welcome",
        val userId: String,
        val roomName: String,
        val participants: List<ParticipantDto>
    ) : WsMessage()

    @Serializable
    data class Reject(
        override val type: String = "reject",
        val reason: String // "full" | "locked" | "invalid_room" | "wrong_password"
    ) : WsMessage()

    @Serializable
    data class Participants(
        override val type: String = "participants",
        val list: List<ParticipantDto>
    ) : WsMessage()

    @Serializable
    data class ChunkMeta(
        override val type: String = "chunk_meta",
        val chunkNo: Int,
        val size: Long,
        val sha256: String,
        val durMs: Long,
        val startMs: Long,
        val totalFrames: Int
    ) : WsMessage()

    @Serializable
    data class Progress(
        override val type: String = "progress",
        val chunkNo: Int,
        val percent: Int
    ) : WsMessage()

    @Serializable
    data class Ready(
        override val type: String = "ready",
        val chunkNo: Int
    ) : WsMessage()

    @Serializable
    data class Waiting(
        override val type: String = "waiting",
        val names: List<SlowUserDto>
    ) : WsMessage()

    @Serializable
    data class PlayAt(
        override val type: String = "play_at",
        val chunkNo: Int,
        val atHostMs: Long,
        val posMs: Long
    ) : WsMessage()

    @Serializable
    data class Pause(
        override val type: String = "pause",
        val posMs: Long
    ) : WsMessage()

    @Serializable
    data class Resume(
        override val type: String = "resume",
        val atHostMs: Long,
        val posMs: Long
    ) : WsMessage()

    @Serializable
    data class Sync(
        override val type: String = "sync",
        val posMs: Long,
        val hostMs: Long
    ) : WsMessage()

    @Serializable
    data class TimeReq(
        override val type: String = "time_req",
        val t0: Long
    ) : WsMessage()

    @Serializable
    data class TimeRes(
        override val type: String = "time_res",
        val t0: Long,
        val hostMs: Long
    ) : WsMessage()

    @Serializable
    data class ChunkDone(
        override val type: String = "chunk_done",
        val chunkNo: Int
    ) : WsMessage()

    @Serializable
    data class Resend(
        override val type: String = "resend",
        val chunkNo: Int
    ) : WsMessage()

    @Serializable
    data class Chat(
        override val type: String = "chat",
        val from: String,
        val text: String,
        val ts: Long,
        val replyTo: String? = null
    ) : WsMessage()

    @Serializable
    data class Reaction(
        override val type: String = "reaction",
        val from: String,
        val emoji: String
    ) : WsMessage()

    @Serializable
    data class SoundEffect(
        override val type: String = "sound_effect",
        val from: String,
        val soundId: String,
        val label: String,
        val emoji: String
    ) : WsMessage()

    @Serializable
    data class VoiceData(
        override val type: String = "voice_data",
        val from: String,
        val pcmBase64: String
    ) : WsMessage()

    @Serializable
    data class VoiceState(
        override val type: String = "voice_state",
        val from: String,
        val isMicOn: Boolean,
        val isSpeaking: Boolean
    ) : WsMessage()

    @Serializable
    data class Kick(
        override val type: String = "kick",
        val userId: String
    ) : WsMessage()

    @Serializable
    data class Mute(
        override val type: String = "mute",
        val userId: String
    ) : WsMessage()

    @Serializable
    data class Seek(
        override val type: String = "seek",
        val targetPosMs: Long
    ) : WsMessage()

    @Serializable
    data class Ended(
        override val type: String = "ended"
    ) : WsMessage()
}

data class BinaryChunkFrame(
    val chunkNo: Int,
    val frameIndex: Int,
    val payload: ByteArray
)

object Protocol {
    const val MAX_FRAME_PAYLOAD_BYTES = 262_144 // 256 KB
    const val BINARY_HEADER_BYTES = 8
    const val MAX_ROOM_PARTICIPANTS = 6 // Host + 5 participants

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeText(msg: WsMessage): String = when (msg) {
        is WsMessage.Join -> json.encodeToString(msg)
        is WsMessage.Welcome -> json.encodeToString(msg)
        is WsMessage.Reject -> json.encodeToString(msg)
        is WsMessage.Participants -> json.encodeToString(msg)
        is WsMessage.ChunkMeta -> json.encodeToString(msg)
        is WsMessage.Progress -> json.encodeToString(msg)
        is WsMessage.Ready -> json.encodeToString(msg)
        is WsMessage.Waiting -> json.encodeToString(msg)
        is WsMessage.PlayAt -> json.encodeToString(msg)
        is WsMessage.Pause -> json.encodeToString(msg)
        is WsMessage.Resume -> json.encodeToString(msg)
        is WsMessage.Sync -> json.encodeToString(msg)
        is WsMessage.TimeReq -> json.encodeToString(msg)
        is WsMessage.TimeRes -> json.encodeToString(msg)
        is WsMessage.ChunkDone -> json.encodeToString(msg)
        is WsMessage.Resend -> json.encodeToString(msg)
        is WsMessage.Chat -> json.encodeToString(msg)
        is WsMessage.Reaction -> json.encodeToString(msg)
        is WsMessage.SoundEffect -> json.encodeToString(msg)
        is WsMessage.VoiceData -> json.encodeToString(msg)
        is WsMessage.VoiceState -> json.encodeToString(msg)
        is WsMessage.Kick -> json.encodeToString(msg)
        is WsMessage.Mute -> json.encodeToString(msg)
        is WsMessage.Seek -> json.encodeToString(msg)
        is WsMessage.Ended -> json.encodeToString(msg)
    }

    fun decodeText(raw: String): WsMessage? {
        return try {
            val element = json.parseToJsonElement(raw)
            val type = element.jsonObject["type"]?.jsonPrimitive?.content ?: return null
            when (type) {
                "join" -> json.decodeFromString<WsMessage.Join>(raw)
                "welcome" -> json.decodeFromString<WsMessage.Welcome>(raw)
                "reject" -> json.decodeFromString<WsMessage.Reject>(raw)
                "participants" -> json.decodeFromString<WsMessage.Participants>(raw)
                "chunk_meta" -> json.decodeFromString<WsMessage.ChunkMeta>(raw)
                "progress" -> json.decodeFromString<WsMessage.Progress>(raw)
                "ready" -> json.decodeFromString<WsMessage.Ready>(raw)
                "waiting" -> json.decodeFromString<WsMessage.Waiting>(raw)
                "play_at" -> json.decodeFromString<WsMessage.PlayAt>(raw)
                "pause" -> json.decodeFromString<WsMessage.Pause>(raw)
                "resume" -> json.decodeFromString<WsMessage.Resume>(raw)
                "sync" -> json.decodeFromString<WsMessage.Sync>(raw)
                "time_req" -> json.decodeFromString<WsMessage.TimeReq>(raw)
                "time_res" -> json.decodeFromString<WsMessage.TimeRes>(raw)
                "chunk_done" -> json.decodeFromString<WsMessage.ChunkDone>(raw)
                "resend" -> json.decodeFromString<WsMessage.Resend>(raw)
                "chat" -> json.decodeFromString<WsMessage.Chat>(raw)
                "reaction" -> json.decodeFromString<WsMessage.Reaction>(raw)
                "sound_effect" -> json.decodeFromString<WsMessage.SoundEffect>(raw)
                "voice_data" -> json.decodeFromString<WsMessage.VoiceData>(raw)
                "voice_state" -> json.decodeFromString<WsMessage.VoiceState>(raw)
                "kick" -> json.decodeFromString<WsMessage.Kick>(raw)
                "mute" -> json.decodeFromString<WsMessage.Mute>(raw)
                "seek" -> json.decodeFromString<WsMessage.Seek>(raw)
                "ended" -> json.decodeFromString<WsMessage.Ended>(raw)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun encodeBinaryFrame(chunkNo: Int, frameIndex: Int, payload: ByteArray, length: Int = payload.size): ByteArray {
        val buffer = ByteBuffer.allocate(BINARY_HEADER_BYTES + length).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(chunkNo)
        buffer.putInt(frameIndex)
        buffer.put(payload, 0, length)
        return buffer.array()
    }

    fun decodeBinaryFrame(bytes: ByteArray): BinaryChunkFrame? {
        if (bytes.size < BINARY_HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val chunkNo = buffer.int
        val frameIndex = buffer.int
        val payload = ByteArray(bytes.size - BINARY_HEADER_BYTES)
        buffer.get(payload)
        return BinaryChunkFrame(chunkNo = chunkNo, frameIndex = frameIndex, payload = payload)
    }
}
