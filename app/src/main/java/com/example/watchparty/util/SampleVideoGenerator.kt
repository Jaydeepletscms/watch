package com.example.watchparty.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates a real H.264 (video/avc) + AAC (audio/mp4a-latm) MP4 video file on-device
 * using Android's hardware/software MediaCodec + MediaMuxer so users can test
 * multi-chunk extraction, SHA-256 verification, WebSocket streaming, and ExoPlayer sync
 * even if their device or emulator has no pre-loaded H.264/AAC MP4 files.
 */
object SampleVideoGenerator {

    suspend fun generateSampleMp4(
        context: Context,
        durationSeconds: Int = 30
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sample_media").apply { mkdirs() }
        File(dir, ".nomedia").apply { if (!exists()) createNewFile() }
        val outFile = File(dir, "watch_party_demo_${durationSeconds}s.mp4")
        if (outFile.exists() && outFile.length() > 32_000L) {
            return@withContext Uri.fromFile(outFile)
        }
        if (outFile.exists()) outFile.delete()

        val width = 640
        val height = 360
        val fps = 15
        val totalFrames = durationSeconds * fps
        val sampleRate = 44100

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 800_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe every 1s for clean chunk boundaries
        }

        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        try {
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoEncoder.start()
            audioEncoder.start()

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            var videoInputDone = false
            var videoOutputDone = false
            var audioInputDone = false
            var audioOutputDone = false

            var frameIndex = 0
            var audioSamplesSubmitted = 0L
            val totalAudioSamples = durationSeconds.toLong() * sampleRate
            val yuvSize = width * height * 3 / 2
            val yuvBuffer = ByteArray(yuvSize)

            fun tryStartMuxer() {
                if (!muxerStarted && videoTrackIndex != -1 && audioTrackIndex != -1) {
                    muxer.start()
                    muxerStarted = true
                }
            }

            while (!videoOutputDone || !audioOutputDone) {
                // 1. Feed Video Input
                if (!videoInputDone) {
                    val inIndex = videoEncoder.dequeueInputBuffer(5_000)
                    if (inIndex >= 0) {
                        if (frameIndex >= totalFrames) {
                            videoEncoder.queueInputBuffer(
                                inIndex, 0, 0,
                                (frameIndex * 1_000_000L) / fps,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            videoInputDone = true
                        } else {
                            fillAnimatedYuv420Frame(yuvBuffer, width, height, frameIndex, fps)
                            val inputBuf = videoEncoder.getInputBuffer(inIndex)!!
                            inputBuf.clear()
                            val bytesToWrite = minOf(inputBuf.remaining(), yuvBuffer.size)
                            inputBuf.put(yuvBuffer, 0, bytesToWrite)
                            val ptsUs = (frameIndex * 1_000_000L) / fps
                            videoEncoder.queueInputBuffer(inIndex, 0, bytesToWrite, ptsUs, 0)
                            frameIndex++
                        }
                    }
                }

                // 2. Feed Audio Input
                if (!audioInputDone) {
                    val inIndex = audioEncoder.dequeueInputBuffer(5_000)
                    if (inIndex >= 0) {
                        if (audioSamplesSubmitted >= totalAudioSamples) {
                            val ptsUs = (audioSamplesSubmitted * 1_000_000L) / sampleRate
                            audioEncoder.queueInputBuffer(
                                inIndex, 0, 0, ptsUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            audioInputDone = true
                        } else {
                            val inputBuf = audioEncoder.getInputBuffer(inIndex)!!
                            inputBuf.clear()
                            val samplesPerBatch = 1024
                            val pcmBytes = ByteArray(samplesPerBatch * 2)
                            val freq = when (((audioSamplesSubmitted / sampleRate) % 3).toInt()) {
                                0 -> 440.0
                                1 -> 554.37
                                else -> 659.25
                            }
                            for (i in 0 until samplesPerBatch) {
                                val t = (audioSamplesSubmitted + i).toDouble() / sampleRate
                                // Gentle chime envelope every second
                                val secPhase = t % 1.0
                                val env = if (secPhase < 0.25) (1.0 - secPhase * 3.5) else 0.12
                                val sample = (sin(2.0 * PI * freq * t) * 5000 * env).toInt().toShort()
                                pcmBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
                                pcmBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                            }
                            inputBuf.put(pcmBytes)
                            val ptsUs = (audioSamplesSubmitted * 1_000_000L) / sampleRate
                            audioEncoder.queueInputBuffer(inIndex, 0, pcmBytes.size, ptsUs, 0)
                            audioSamplesSubmitted += samplesPerBatch
                        }
                    }
                }

                // 3. Drain Video Output
                if (!videoOutputDone) {
                    val outIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 5_000)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                            tryStartMuxer()
                        }
                        outIndex >= 0 -> {
                            val outBuf: ByteBuffer? = videoEncoder.getOutputBuffer(outIndex)
                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                videoBufferInfo.size = 0
                            }
                            if (videoBufferInfo.size > 0 && muxerStarted && outBuf != null) {
                                outBuf.position(videoBufferInfo.offset)
                                outBuf.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, outBuf, videoBufferInfo)
                            }
                            videoEncoder.releaseOutputBuffer(outIndex, false)
                            if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                videoOutputDone = true
                            }
                        }
                    }
                }

                // 4. Drain Audio Output
                if (!audioOutputDone) {
                    val outIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 5_000)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                            tryStartMuxer()
                        }
                        outIndex >= 0 -> {
                            val outBuf: ByteBuffer? = audioEncoder.getOutputBuffer(outIndex)
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                audioBufferInfo.size = 0
                            }
                            if (audioBufferInfo.size > 0 && muxerStarted && outBuf != null) {
                                outBuf.position(audioBufferInfo.offset)
                                outBuf.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                muxer.writeSampleData(audioTrackIndex, outBuf, audioBufferInfo)
                            }
                            audioEncoder.releaseOutputBuffer(outIndex, false)
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                audioOutputDone = true
                            }
                        }
                    }
                }
            }
        } finally {
            try { videoEncoder.stop() } catch (_: Exception) {}
            try { videoEncoder.release() } catch (_: Exception) {}
            try { audioEncoder.stop() } catch (_: Exception) {}
            try { audioEncoder.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
        }

        Uri.fromFile(outFile)
    }

    private fun fillAnimatedYuv420Frame(
        yuv: ByteArray,
        width: Int,
        height: Int,
        frameIndex: Int,
        fps: Int
    ) {
        val frameSize = width * height
        val second = frameIndex / fps
        val progressBarX = ((frameIndex % (fps * 10)).toFloat() / (fps * 10f) * width).toInt()

        // Fill Luma (Y) with moving diagonal wave + progress bar + chunk indicator
        for (y in 0 until height) {
            val rowOffset = y * width
            val isProgressRow = y in (height - 28) until (height - 12)
            for (x in 0 until width) {
                yuv[rowOffset + x] = when {
                    isProgressRow && x <= progressBarX -> 235.toByte()
                    isProgressRow -> 40.toByte()
                    else -> {
                        val v = 60 + ((x + y + frameIndex * 6) % 140)
                        v.toByte()
                    }
                }
            }
        }

        // Fill Chroma (UV / NV12 / I420 compatible shifting hue per chunk)
        val uBase = when ((second / 10) % 3) {
            0 -> 170 // Purple-blue tint (Chunk 1)
            1 -> 90  // Teal-green tint (Chunk 2)
            else -> 140 // Warm magenta tint (Chunk 3)
        }
        val vBase = when ((second / 10) % 3) {
            0 -> 165
            1 -> 100
            else -> 200
        }
        for (i in frameSize until yuv.size step 2) {
            yuv[i] = uBase.toByte()
            if (i + 1 < yuv.size) {
                yuv[i + 1] = vBase.toByte()
            }
        }
    }
}
