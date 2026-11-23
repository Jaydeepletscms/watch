package com.example.watchparty.media

import android.util.Log
import com.example.watchparty.net.BinaryChunkFrame
import com.example.watchparty.net.ClientConnection
import com.example.watchparty.net.Protocol
import com.example.watchparty.net.WsMessage
import com.example.watchparty.util.Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.BitSet

data class ActiveChunkDownload(
    val meta: WsMessage.ChunkMeta,
    val partFile: File,
    val receivedFrames: BitSet = BitSet(meta.totalFrames),
    var lastReportedPercent: Int = 0
)

sealed class ReceiverEvent {
    data class ChunkVerified(val chunk: ChunkInfo) : ReceiverEvent()
    data class ShaMismatchRetry(val chunkNo: Int, val retryAttempt: Int) : ReceiverEvent()
    data class FatalError(val message: String) : ReceiverEvent()
}

/**
 * Section 7: Receiver (media/ChunkReceiver.kt)
 * Reassembles 256 KB binary frames at exact file offsets, reports progress every 5%,
 * verifies size & SHA-256, renames to chunk_N.mp4, sends ready(N), or requests resend up to 2 times.
 */
class ChunkReceiver(
    private val chunkStore: ChunkStore,
    private val clientConnection: ClientConnection
) {
    private val tag = "ChunkReceiver"
    private val mutex = Mutex()
    private val activeDownloads = mutableMapOf<Int, ActiveChunkDownload>()
    private val retryCounts = mutableMapOf<Int, Int>()

    private val _downloadProgress = MutableStateFlow<Pair<Int, Int>?>(null) // (chunkNo, percent)
    val downloadProgress: StateFlow<Pair<Int, Int>?> = _downloadProgress.asStateFlow()

    private val _events = MutableSharedFlow<ReceiverEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<ReceiverEvent> = _events.asSharedFlow()

    suspend fun onChunkMeta(meta: WsMessage.ChunkMeta) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val partFile = chunkStore.getParticipantPartFile(meta.chunkNo)
            if (partFile.exists()) partFile.delete()
            RandomAccessFile(partFile, "rw").use { raf ->
                raf.setLength(0L)
            }
            activeDownloads[meta.chunkNo] = ActiveChunkDownload(
                meta = meta,
                partFile = partFile
            )
            _downloadProgress.value = meta.chunkNo to 0
        }
    }

    suspend fun onBinaryFrame(frame: BinaryChunkFrame) = withContext(Dispatchers.IO) {
        var completedDownload: ActiveChunkDownload? = null

        mutex.withLock {
            val download = activeDownloads[frame.chunkNo] ?: return@withContext
            val offset = frame.frameIndex.toLong() * Protocol.MAX_FRAME_PAYLOAD_BYTES.toLong()
            RandomAccessFile(download.partFile, "rw").use { raf ->
                raf.seek(offset)
                raf.write(frame.payload)
            }
            download.receivedFrames.set(frame.frameIndex)

            val receivedCount = download.receivedFrames.cardinality()
            val total = download.meta.totalFrames.coerceAtLeast(1)
            val percent = ((receivedCount * 100) / total).coerceIn(0, 100)
            _downloadProgress.value = frame.chunkNo to percent

            // Section 7.3: Report progress every 5%
            if (percent - download.lastReportedPercent >= 5 || percent == 100) {
                download.lastReportedPercent = percent
                clientConnection.send(WsMessage.Progress(chunkNo = frame.chunkNo, percent = percent))
            }

            if (receivedCount >= total) {
                activeDownloads.remove(frame.chunkNo)
                completedDownload = download
            }
        }

        completedDownload?.let { verifyAndFinalize(it) }
    }

    private suspend fun verifyAndFinalize(download: ActiveChunkDownload) {
        val meta = download.meta
        val partFile = download.partFile

        val actualSize = partFile.length()
        val actualSha = if (partFile.exists()) Hash.sha256(partFile) else ""

        if (actualSize == meta.size && actualSha.equals(meta.sha256, ignoreCase = true)) {
            val finalFile = chunkStore.getParticipantChunkFile(meta.chunkNo)
            if (finalFile.exists()) finalFile.delete()
            val renamed = partFile.renameTo(finalFile)
            val resolvedFile = if (renamed) finalFile else {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
                finalFile
            }
            retryCounts.remove(meta.chunkNo)
            _downloadProgress.value = null

            val chunkInfo = ChunkInfo(
                chunkNo = meta.chunkNo,
                file = resolvedFile,
                startMs = meta.startMs,
                durMs = meta.durMs,
                size = meta.size,
                sha256 = meta.sha256
            )
            clientConnection.send(WsMessage.Ready(chunkNo = meta.chunkNo))
            _events.emit(ReceiverEvent.ChunkVerified(chunkInfo))
        } else {
            Log.e(tag, "Chunk ${meta.chunkNo} SHA/size mismatch: expected ${meta.sha256} (${meta.size}B), got $actualSha (${actualSize}B)")
            partFile.delete()
            val attempts = (retryCounts[meta.chunkNo] ?: 0) + 1
            retryCounts[meta.chunkNo] = attempts

            // Section 7.5: Max 2 retries on SHA mismatch, then show error
            if (attempts <= 2) {
                _events.emit(ReceiverEvent.ShaMismatchRetry(meta.chunkNo, attempts))
                clientConnection.send(WsMessage.Resend(chunkNo = meta.chunkNo))
            } else {
                _events.emit(
                    ReceiverEvent.FatalError(
                        "Chunk ${meta.chunkNo} SHA-256 verification failed after 2 retries."
                    )
                )
            }
        }
    }

    fun onChunkPlayedAndDeleted(chunkNo: Int) {
        chunkStore.deleteParticipantChunk(chunkNo)
        clientConnection.send(WsMessage.ChunkDone(chunkNo = chunkNo))
    }

    fun reset() {
        activeDownloads.clear()
        retryCounts.clear()
        _downloadProgress.value = null
    }
}
