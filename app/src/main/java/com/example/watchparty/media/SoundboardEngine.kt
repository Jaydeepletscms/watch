package com.example.watchparty.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

data class SoundboardItem(
    val id: String,
    val label: String,
    val emoji: String
)

typealias SoundEffectItem = SoundboardItem

/**
 * Live Soundboard Engine (Meme & Theatre Sound Effects)
 * Synthesizes crisp, unmistakable multi-harmonic audio effects on the fly via AudioTrack
 * so everyone in the Watch Party hears them simultaneously with zero external asset overhead.
 */
class SoundboardEngine(
    private val scope: CoroutineScope
) {
    private val sampleRate = 24000

    val items: List<SoundboardItem> = SOUNDBOARD_ITEMS

    fun release() {
        // One-shot AudioTracks are stopped & released automatically after playback
    }

    companion object {
        val SOUNDBOARD_ITEMS: List<SoundboardItem> = listOf(
            SoundboardItem("airhorn", "Airhorn", "📢"),
            SoundboardItem("rimshot", "Ba-Dum-Tss", "🥁"),
            SoundboardItem("applause", "Applause", "👏"),
            SoundboardItem("laugh", "Comedy Boing", "😂"),
            SoundboardItem("suspense", "Drama Drop", "😱"),
            SoundboardItem("whistle", "Party Hype", "🎉")
        )
    }

    fun playSoundEffect(soundId: String, volume: Float = 0.85f) {
        scope.launch(Dispatchers.Default) {
            try {
                val pcm = when (soundId) {
                    "airhorn" -> generateAirhorn()
                    "rimshot" -> generateRimshot()
                    "applause" -> generateApplause()
                    "laugh" -> generateComedyBoing()
                    "suspense" -> generateDramaDrop()
                    "whistle" -> generatePartyWhistle()
                    else -> generateAirhorn()
                }
                playPcmOneShot(pcm, volume)
            } catch (_: Exception) {
            }
        }
    }

    private fun playPcmOneShot(pcm: ShortArray, volume: Float) {
        val byteLen = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(byteLen)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.setVolume(volume.coerceIn(0f, 1f))
        track.play()
        Thread.sleep((pcm.size * 1000L / sampleRate) + 120L)
        try {
            track.stop()
            track.release()
        } catch (_: Exception) {
        }
    }

    // 1. DJ Stadium Airhorn (Triple blast with detuned brass harmonics)
    private fun generateAirhorn(): ShortArray {
        val durSec = 0.95
        val total = (sampleRate * durSec).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val active = (t in 0.0..0.16) || (t in 0.20..0.36) || (t in 0.40..0.92)
            if (!active) continue
            val f1 = 466.16
            val f2 = 622.25
            val f3 = 932.33
            val wave = 0.45 * sin(2 * PI * f1 * t) +
                0.35 * sin(2 * PI * f2 * t) +
                0.20 * sin(2 * PI * f3 * t)
            // Soft clipping for brassy horn texture
            val driven = (wave * 1.6).coerceIn(-0.95, 0.95)
            out[i] = (driven * 22000).toInt().toShort()
        }
        return out
    }

    // 2. Ba-Dum-Tss (Kick -> Snare -> Cymbal shimmer)
    private fun generateRimshot(): ShortArray {
        val durSec = 0.85
        val total = (sampleRate * durSec).toInt()
        val out = ShortArray(total)
        val rng = Random(42)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val v = when {
                t < 0.16 -> {
                    val lt = t
                    val freq = 140.0 * exp(-lt * 10.0) + 55.0
                    sin(2 * PI * freq * lt) * exp(-lt * 14.0)
                }
                t in 0.18..0.36 -> {
                    val lt = t - 0.18
                    val tone = sin(2 * PI * 210.0 * lt) * exp(-lt * 18.0)
                    val noise = (rng.nextDouble() * 2.0 - 1.0) * exp(-lt * 22.0) * 0.5
                    tone + noise
                }
                t >= 0.38 -> {
                    val lt = t - 0.38
                    val noise = (rng.nextDouble() * 2.0 - 1.0)
                    val ring = sin(2 * PI * 3200.0 * lt) * 0.4
                    (noise * 0.6 + ring) * exp(-lt * 6.5)
                }
                else -> 0.0
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * 23000).toInt().toShort()
        }
        return out
    }

    // 3. Applause / Multi-clap burst
    private fun generateApplause(): ShortArray {
        val durSec = 0.90
        val total = (sampleRate * durSec).toInt()
        val out = ShortArray(total)
        val rng = Random(101)
        val clapTimes = listOf(0.02, 0.11, 0.19, 0.28, 0.36, 0.45, 0.54, 0.63, 0.72)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            var env = 0.0
            for (ct in clapTimes) {
                if (t >= ct && t < ct + 0.08) {
                    env += exp(-(t - ct) * 42.0)
                }
            }
            val noise = rng.nextDouble() * 2.0 - 1.0
            val band = sin(2 * PI * 1400.0 * t) * 0.5 + 0.5
            val v = noise * band * env.coerceAtMost(1.0)
            out[i] = (v.coerceIn(-1.0, 1.0) * 20000).toInt().toShort()
        }
        return out
    }

    // 4. Playful Upward Comedy Boing + Arpeggio
    private fun generateComedyBoing(): ShortArray {
        val durSec = 0.75
        val total = (sampleRate * durSec).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val freq = 260.0 + 380.0 * sin(2 * PI * 6.5 * t) * (1.0 - t) + (t * 420.0)
            val env = exp(-t * 2.5)
            val v = sin(2 * PI * freq * t) * env
            out[i] = (v.coerceIn(-1.0, 1.0) * 21000).toInt().toShort()
        }
        return out
    }

    // 5. Dramatic Cinema Bass Drop ("Dun-Dun-DUUUN")
    private fun generateDramaDrop(): ShortArray {
        val durSec = 1.0
        val total = (sampleRate * durSec).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val freq = when {
                t < 0.22 -> 196.0 // G3
                t < 0.44 -> 185.0 // F#3
                else -> 130.81 * (1.0 - (t - 0.44) * 0.25) // C3 downward drop
            }
            val localT = when {
                t < 0.22 -> t
                t < 0.44 -> t - 0.22
                else -> t - 0.44
            }
            val env = exp(-localT * 3.8)
            val wave = 0.6 * sin(2 * PI * freq * t) + 0.4 * sin(2 * PI * (freq * 2.0) * t)
            out[i] = ((wave * env).coerceIn(-1.0, 1.0) * 23000).toInt().toShort()
        }
        return out
    }

    // 6. Rising Party Hype Whistle
    private fun generatePartyWhistle(): ShortArray {
        val durSec = 0.80
        val total = (sampleRate * durSec).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val freq = 680.0 + (t * t) * 1650.0 + 35.0 * sin(2 * PI * 18.0 * t)
            val env = if (t < 0.7) 1.0 else (0.80 - t) * 10.0
            val v = sin(2 * PI * freq * t) * env.coerceIn(0.0, 1.0)
            out[i] = (v * 20000).toInt().toShort()
        }
        return out
    }
}
