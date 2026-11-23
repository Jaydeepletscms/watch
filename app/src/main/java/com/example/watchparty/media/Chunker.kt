package com.example.watchparty.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.watchparty.util.Hash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class UnsupportedFormatException(message: String) : Exception(message)

data class VideoProbeResult(
    val isSupported: Boolean,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoMime: String,
    val audioMime: String,
    val errorMessage: String? = null
)

/**
 * Section 6: Chunker (media/Chunker.kt)
 * Splits a local H.264/AAC MP4 video into lossless chunks using MediaExtractor + MediaMuxer
 * without re-encoding, rebasing timestamps per chunk and keeping audio-video lip-sync aligned.
 */
class Chunker(
    private val context: Context,
    private val chunkStore: ChunkStore
) {

    suspend fun inspectVideo(uri: Uri): VideoProbeResult = withContext(Dispatchers.IO) {
        var fileName = uri.lastPathSegment ?: "video.mp4"
        var fileSize = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
        }
        if (fileSize == 0L && uri.scheme == "file") {
            uri.path?.let { path ->
                val f = java.io.File(path)
                fileName = f.name
                fileSize = f.length()
            }
        }

        val extractor = MediaExtractor()
        try {
            setDataSourceCompat(extractor, uri)
            var videoMime = ""
            var audioMime = ""
            var width = 0
            var height = 0
            var durationUs = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoMime.isEmpty()) {
                    videoMime = mime
                    width = format.getIntegerOrDefault(MediaFormat.KEY_WIDTH, 1280)
                    height = format.getIntegerOrDefault(MediaFormat.KEY_HEIGHT, 720)
                    durationUs = maxOf(durationUs, format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L))
                } else if (mime.startsWith("audio/") && audioMime.isEmpty()) {
                    audioMime = mime
                    durationUs = maxOf(durationUs, format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L))
                }
            }

            val isAvc = videoMime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
            val isAac = audioMime.isEmpty() || audioMime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true)

            if (!isAvc || !isAac) {
                return@withContext VideoProbeResult(
                    isSupported = false,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    durationMs = durationUs / 1000L,
                    width = width,
                    height = height,
                    videoMime = videoMime.ifEmpty { "none" },
                    audioMime = audioMime.ifEmpty { "none" },
                    errorMessage = "Sirf H.264/AAC mp4 supported hai (Found: $videoMime / $audioMime)"
                )
            }

            VideoProbeResult(
                isSupported = true,
                fileName = fileName,
                fileSizeBytes = fileSize,
                durationMs = durationUs / 1000L,
                width = width,
                height = height,
                videoMime = "H.264 (AVC)",
                audioMime = if (audioMime.isEmpty()) "None" else "AAC"
            )
        } catch (e: Exception) {
            VideoProbeResult(
                isSupported = false,
                fileName = fileName,
                fileSizeBytes = fileSize,
                durationMs = 0L,
                width = 0,
                height = 0,
                videoMime = "unknown",
                audioMime = "unknown",
                errorMessage = "Sirf H.264/AAC mp4 supported hai (${e.localizedMessage ?: "Invalid file"})"
            )
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Produces a Flow of ChunkInfo with a bounded buffer of 2 chunks ahead (Section 6 pipeline rule).
     */
    fun chunkVideoFlow(
        uri: Uri,
        firstChunkDurationMs: Long = 10_000L,
        subsequentChunkDurationMs: Long = 10_000L,
        startFromMs: Long = 0L,
        startingChunkNo: Int = 1,
        maxChunks: Int = Int.MAX_VALUE
    ): Flow<ChunkInfo> = flow {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()

        try {
            setDataSourceCompat(videoExtractor, uri)
            setDataSourceCompat(audioExtractor, uri)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            var totalDurationUs = 0L

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    if (!mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        throw UnsupportedFormatException("Sirf H.264/AAC mp4 supported hai (Video: $mime)")
                    }
                    videoTrackIndex = i
                    videoFormat = format
                    totalDurationUs = maxOf(totalDurationUs, format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L))
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    if (!mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true)) {
                        throw UnsupportedFormatException("Sirf H.264/AAC mp4 supported hai (Audio: $mime)")
                    }
                    audioTrackIndex = i
                    audioFormat = format
                    totalDurationUs = maxOf(totalDurationUs, format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L))
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                throw UnsupportedFormatException("Sirf H.264/AAC mp4 supported hai (Video track nahi mila)")
            }

            videoExtractor.selectTrack(videoTrackIndex)
            if (audioTrackIndex != -1) {
                audioExtractor.selectTrack(audioTrackIndex)
            }

            if (startFromMs > 0L) {
                val seekUs = startFromMs * 1000L
                videoExtractor.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val actualVideoStartUs = videoExtractor.sampleTime.coerceAtLeast(0L)
                if (audioTrackIndex != -1) {
                    audioExtractor.seekTo(actualVideoStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }
            }

            val maxSampleSize = videoFormat.getIntegerOrDefault(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
                .coerceAtLeast(512 * 1024)
            val buffer = ByteBuffer.allocateDirect(maxSampleSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var chunkNo = startingChunkNo
            var chunksCreated = 0

            while (chunksCreated < maxChunks) {
                val chunkStartUs = videoExtractor.sampleTime
                if (chunkStartUs < 0L) break // End of stream

                val targetDurMs = if (chunksCreated == 0) firstChunkDurationMs else subsequentChunkDurationMs
                val targetEndUs = chunkStartUs + (targetDurMs * 1000L)

                // Find next keyframe boundary at or after targetEndUs using a probe extractor
                val probeExtractor = MediaExtractor()
                val chunkEndUs: Long = try {
                    setDataSourceCompat(probeExtractor, uri)
                    probeExtractor.selectTrack(videoTrackIndex)
                    probeExtractor.seekTo(targetEndUs, MediaExtractor.SEEK_TO_NEXT_SYNC)
                    val nextSyncUs = probeExtractor.sampleTime
                    if (nextSyncUs <= chunkStartUs) Long.MAX_VALUE else nextSyncUs
                } finally {
                    try { probeExtractor.release() } catch (_: Exception) {}
                }

                val outFile = chunkStore.getHostChunkFile(chunkNo)
                if (outFile.exists()) outFile.delete()

                val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxVideoTrack = muxer.addTrack(videoFormat)
                val muxAudioTrack = if (audioFormat != null) muxer.addTrack(audioFormat) else -1
                muxer.start()

                var lastVideoSampleUs = chunkStartUs
                try {
                    // Write Video samples in [chunkStartUs, chunkEndUs)
                    while (true) {
                        val sampleTimeUs = videoExtractor.sampleTime
                        if (sampleTimeUs < 0L || sampleTimeUs >= chunkEndUs) break
                        buffer.clear()
                        val sampleSize = videoExtractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = (sampleTimeUs - chunkStartUs).coerceAtLeast(0L)
                        bufferInfo.flags = mapExtractorFlags(videoExtractor.sampleFlags)
                        muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                        lastVideoSampleUs = sampleTimeUs
                        videoExtractor.advance()
                    }

                    val effectiveEndUs = if (chunkEndUs == Long.MAX_VALUE) {
                        maxOf(lastVideoSampleUs + 33_333L, totalDurationUs)
                    } else {
                        chunkEndUs
                    }

                    // Write Audio samples strictly within [chunkStartUs, effectiveEndUs) for lip-sync
                    if (muxAudioTrack != -1) {
                        while (true) {
                            val audioTimeUs = audioExtractor.sampleTime
                            if (audioTimeUs < 0L || audioTimeUs >= effectiveEndUs) break
                            if (audioTimeUs < chunkStartUs) {
                                audioExtractor.advance()
                                continue
                            }
                            buffer.clear()
                            val sampleSize = audioExtractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) break

                            bufferInfo.offset = 0
                            bufferInfo.size = sampleSize
                            bufferInfo.presentationTimeUs = (audioTimeUs - chunkStartUs).coerceAtLeast(0L)
                            bufferInfo.flags = mapExtractorFlags(audioExtractor.sampleFlags)
                            muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                            audioExtractor.advance()
                        }
                    }
                } finally {
                    try { muxer.stop() } catch (_: Exception) {}
                    try { muxer.release() } catch (_: Exception) {}
                }

                val actualEndUs = if (chunkEndUs == Long.MAX_VALUE) {
                    maxOf(lastVideoSampleUs + 33_333L, totalDurationUs, chunkStartUs + 100_000L)
                } else {
                    chunkEndUs
                }
                val chunkDurMs = ((actualEndUs - chunkStartUs) / 1000L).coerceAtLeast(100L)
                val sha = Hash.sha256(outFile)

                emit(
                    ChunkInfo(
                        chunkNo = chunkNo,
                        file = outFile,
                        startMs = chunkStartUs / 1000L,
                        durMs = chunkDurMs,
                        size = outFile.length(),
                        sha256 = sha
                    )
                )

                chunkNo++
                chunksCreated++

                if (videoExtractor.sampleTime < 0L) break
            }
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
        }
    }.buffer(capacity = 2, onBufferOverflow = BufferOverflow.SUSPEND) // Section 6: lead buffer of 2 chunks
        .flowOn(Dispatchers.IO)

    private fun setDataSourceCompat(extractor: MediaExtractor, uri: Uri) {
        if (uri.scheme == "file" && uri.path != null) {
            extractor.setDataSource(uri.path!!)
        } else {
            extractor.setDataSource(context, uri, null)
        }
    }

    private fun mapExtractorFlags(sampleFlags: Int): Int {
        var flags = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
        return if (containsKey(key)) getInteger(key) else default
    }

    private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long {
        return if (containsKey(key)) getLong(key) else default
    }
}
