package com.example.watchparty.media

import com.example.watchparty.net.HostServer
import com.example.watchparty.net.Protocol
import com.example.watchparty.net.WsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.FileInputStream

/**
 * Section 5.2 & 5.3: ChunkSender (media/ChunkSender.kt)
 * Sends chunk_meta followed by 256 KB binary frames with 8-byte header (chunkNo, frameIndex).
 * Yields between every frame so control messages (pause, resume, chat, reaction) are never blocked.
 */
class ChunkSender(
    private val hostServer: HostServer
) {

    suspend fun broadcastChunk(chunk: ChunkInfo) = withContext(Dispatchers.IO) {
        if (!chunk.file.exists()) return@withContext

        hostServer.resetParticipantsDownloadProgress(chunk.chunkNo)
        val meta = WsMessage.ChunkMeta(
            chunkNo = chunk.chunkNo,
            size = chunk.size,
            sha256 = chunk.sha256,
            durMs = chunk.durMs,
            startMs = chunk.startMs,
            totalFrames = chunk.totalFrames
        )
        hostServer.broadcastText(meta)

        val buffer = ByteArray(Protocol.MAX_FRAME_PAYLOAD_BYTES)
        var frameIndex = 0
        FileInputStream(chunk.file).use { fis ->
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                val frameBytes = Protocol.encodeBinaryFrame(
                    chunkNo = chunk.chunkNo,
                    frameIndex = frameIndex,
                    payload = buffer,
                    length = bytesRead
                )
                hostServer.broadcastBinaryFrame(frameBytes)
                frameIndex++
                // Section 5.3 rule: yield between frames so control messages can interleave immediately
                yield()
                bytesRead = fis.read(buffer)
            }
        }
    }

    suspend fun sendChunkToUser(userId: String, chunk: ChunkInfo) = withContext(Dispatchers.IO) {
        if (!chunk.file.exists()) return@withContext

        val meta = WsMessage.ChunkMeta(
            chunkNo = chunk.chunkNo,
            size = chunk.size,
            sha256 = chunk.sha256,
            durMs = chunk.durMs,
            startMs = chunk.startMs,
            totalFrames = chunk.totalFrames
        )
        hostServer.sendTextToUser(userId, meta)

        val buffer = ByteArray(Protocol.MAX_FRAME_PAYLOAD_BYTES)
        var frameIndex = 0
        FileInputStream(chunk.file).use { fis ->
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                val frameBytes = Protocol.encodeBinaryFrame(
                    chunkNo = chunk.chunkNo,
                    frameIndex = frameIndex,
                    payload = buffer,
                    length = bytesRead
                )
                hostServer.sendBinaryFrameToUser(userId, frameBytes)
                frameIndex++
                yield()
                bytesRead = fis.read(buffer)
            }
        }
    }
}
