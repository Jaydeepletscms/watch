package com.example.watchparty.media

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Real-time Voice Sharing Engine (Walkie-Talkie & Live Mic Party Voice)
 * Captures 16kHz 16-bit Mono PCM from the microphone, applies a Voice Activity Detection (VAD)
 * noise gate, streams compact PCM packets over WebSocket, and plays incoming voice audio via AudioTrack.
 */
class VoiceEngine(
    private val scope: CoroutineScope,
    private val onSendVoicePacket: (pcmBase64: String) -> Unit,
    private val onVoiceStateChanged: (isMicOn: Boolean, isSpeaking: Boolean) -> Unit
) {
    private val tag = "VoiceEngine"
    private val sampleRate = 16000
    private val frameBytes = 1600 // 50ms of 16kHz 16-bit mono audio (800 samples * 2 bytes)

    private val _isMicOn = MutableStateFlow(false)
    val isMicOn: StateFlow<Boolean> = _isMicOn.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isLocalSpeaking = MutableStateFlow(false)
    val isLocalSpeaking: StateFlow<Boolean> = _isLocalSpeaking.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _voiceVolume = MutableStateFlow(1.0f)
    val voiceVolume: StateFlow<Float> = _voiceVolume.asStateFlow()

    private val _activeRemoteSpeaker = MutableStateFlow<String?>(null)
    val activeRemoteSpeaker: StateFlow<String?> = _activeRemoteSpeaker.asStateFlow()

    private var recordJob: Job? = null
    private var speakerResetJob: Job? = null
    private var audioTrack: AudioTrack? = null

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameBytes * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
        } catch (e: Exception) {
            Log.e(tag, "AudioTrack init failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun setMicEnabled(enabled: Boolean) {
        if (_isMicOn.value == enabled) return
        _isMicOn.value = enabled
        if (!enabled) {
            recordJob?.cancel()
            recordJob = null
            _isLocalSpeaking.value = false
            _micLevel.value = 0f
            onVoiceStateChanged(false, false)
            return
        }

        onVoiceStateChanged(true, false)
        recordJob?.cancel()
        recordJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(frameBytes * 4)

                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    _isMicOn.value = false
                    onVoiceStateChanged(false, false)
                    return@launch
                }

                recorder.startRecording()
                val buffer = ByteArray(frameBytes)
                var wasSpeaking = false
                var hangCounter = 0

                while (isActive && _isMicOn.value) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val rms = computeNormalizedRms(buffer, read)
                        _micLevel.value = rms
                        val speakingNow = rms > 0.035f
                        if (speakingNow) {
                            hangCounter = 6 // Keep gate open for ~300ms after speech
                        } else if (hangCounter > 0) {
                            hangCounter--
                        }

                        val gateOpen = speakingNow || hangCounter > 0
                        if (gateOpen != wasSpeaking) {
                            wasSpeaking = gateOpen
                            _isLocalSpeaking.value = gateOpen
                            onVoiceStateChanged(true, gateOpen)
                        }

                        if (gateOpen) {
                            val slice = if (read == buffer.size) buffer else buffer.copyOf(read)
                            val encoded = Base64.encodeToString(slice, Base64.NO_WRAP)
                            onSendVoicePacket(encoded)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "AudioRecord error: ${e.message}")
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                try {
                    recorder?.release()
                } catch (_: Exception) {
                }
                _isLocalSpeaking.value = false
                _micLevel.value = 0f
            }
        }
    }

    fun toggleSpeaker(): Boolean {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        applyTrackVolume()
        return next
    }

    fun setVoiceVolume(volume: Float) {
        _voiceVolume.value = volume.coerceIn(0f, 1f)
        applyTrackVolume()
    }

    private fun applyTrackVolume() {
        val effective = if (_isSpeakerOn.value) _voiceVolume.value else 0f
        try {
            audioTrack?.setVolume(effective)
        } catch (_: Exception) {
        }
    }

    fun onIncomingVoicePacket(senderName: String, pcmBase64: String) {
        _activeRemoteSpeaker.value = senderName
        speakerResetJob?.cancel()
        speakerResetJob = scope.launch {
            delay(650L)
            if (_activeRemoteSpeaker.value == senderName) {
                _activeRemoteSpeaker.value = null
            }
        }

        if (!_isSpeakerOn.value || _voiceVolume.value <= 0.01f) return
        scope.launch(Dispatchers.IO) {
            try {
                val pcmBytes = Base64.decode(pcmBase64, Base64.NO_WRAP)
                val track = audioTrack ?: return@launch
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(pcmBytes, 0, pcmBytes.size)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun computeNormalizedRms(pcm16: ByteArray, length: Int): Float {
        val samples = length / 2
        if (samples <= 0) return 0f
        var sumSq = 0.0
        var i = 0
        while (i + 1 < length) {
            val lo = pcm16[i].toInt() and 0xFF
            val hi = pcm16[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val norm = sample / 32768.0
            sumSq += norm * norm
            i += 2
        }
        val rms = sqrt(sumSq / samples).toFloat()
        return (rms * 3.2f).coerceIn(0f, 1f)
    }

    fun release() {
        recordJob?.cancel()
        speakerResetJob?.cancel()
        _isMicOn.value = false
        _isLocalSpeaking.value = false
        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }
}
