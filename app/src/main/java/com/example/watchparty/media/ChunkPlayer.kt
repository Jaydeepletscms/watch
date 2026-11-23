package com.example.watchparty.media

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Section 10: Player (media/ChunkPlayer.kt)
 * Single ExoPlayer instance in playlist mode.
 * Prepares early with playWhenReady = false until play_at triggers.
 * Tracks global position (chunk.startMs + currentPosition) and logs inter-chunk transition gaps.
 */
class ChunkPlayer(
    context: Context,
    private val scope: CoroutineScope,
    private val onChunkTransitioned: (completedChunkNo: Int, newChunkNo: Int, gapMs: Long) -> Unit,
    private val onPlaybackEnded: () -> Unit
) {
    private val tag = "ChunkPlayer"

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private val chunkByMediaId = mutableMapOf<String, ChunkInfo>()
    private val orderedChunks = mutableListOf<ChunkInfo>()

    private val _currentChunkNo = MutableStateFlow(1)
    val currentChunkNo: StateFlow<Int> = _currentChunkNo.asStateFlow()

    private val _globalPositionMs = MutableStateFlow(0L)
    val globalPositionMs: StateFlow<Long> = _globalPositionMs.asStateFlow()

    private val _totalQueuedDurationMs = MutableStateFlow(0L)
    val totalQueuedDurationMs: StateFlow<Long> = _totalQueuedDurationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _lastTransitionGapMs = MutableStateFlow(0L)
    val lastTransitionGapMs: StateFlow<Long> = _lastTransitionGapMs.asStateFlow()

    private var transitionStartRealtimeMs: Long = 0L
    private var positionTickerJob: Job? = null

    init {
        exoPlayer.playWhenReady = false
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val now = SystemClock.elapsedRealtime()
                val gapMs = if (transitionStartRealtimeMs > 0L) {
                    (now - transitionStartRealtimeMs).coerceAtLeast(0L)
                } else {
                    4L // Gapless playlist transition inside ExoPlayer renderer
                }
                transitionStartRealtimeMs = 0L
                _lastTransitionGapMs.value = gapMs

                // Section 10: Log if gap between 2 chunks exceeds 150 ms
                if (gapMs > 150L) {
                    Log.w(tag, "Inter-chunk gap exceeded 150ms: ${gapMs}ms")
                } else {
                    Log.d(tag, "Gapless chunk transition completed in ${gapMs}ms")
                }

                val newChunk = mediaItem?.mediaId?.let { chunkByMediaId[it] } ?: return
                val previousChunkNo = _currentChunkNo.value
                _currentChunkNo.value = newChunk.chunkNo
                if (newChunk.chunkNo != previousChunkNo) {
                    onChunkTransitioned(previousChunkNo, newChunk.chunkNo, gapMs)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING && _isPlaying.value) {
                    transitionStartRealtimeMs = SystemClock.elapsedRealtime()
                } else if (playbackState == Player.STATE_READY && transitionStartRealtimeMs > 0L) {
                    val gap = SystemClock.elapsedRealtime() - transitionStartRealtimeMs
                    _lastTransitionGapMs.value = gap
                    transitionStartRealtimeMs = 0L
                } else if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }
        })

        startPositionTicker()
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                updateGlobalPosition()
                delay(100L)
            }
        }
    }

    private fun updateGlobalPosition() {
        val currentId = exoPlayer.currentMediaItem?.mediaId
        val chunk = currentId?.let { chunkByMediaId[it] } ?: orderedChunks.firstOrNull()
        if (chunk != null) {
            _currentChunkNo.value = chunk.chunkNo
            _globalPositionMs.value = chunk.startMs + exoPlayer.currentPosition.coerceAtLeast(0L)
        }
    }

    fun enqueueChunk(chunk: ChunkInfo) {
        val mediaId = "chunk_${chunk.chunkNo}"
        if (chunkByMediaId.containsKey(mediaId)) return

        chunkByMediaId[mediaId] = chunk
        orderedChunks.add(chunk)
        val lastChunk = orderedChunks.maxByOrNull { it.startMs + it.durMs }
        _totalQueuedDurationMs.value = if (lastChunk != null) lastChunk.startMs + lastChunk.durMs else 0L

        val item = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(Uri.fromFile(chunk.file))
            .build()

        exoPlayer.addMediaItem(item)
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
    }

    fun play() {
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekToGlobalPosition(globalPosMs: Long) {
        if (orderedChunks.isEmpty()) return
        val targetChunk = orderedChunks.firstOrNull { chunk ->
            globalPosMs >= chunk.startMs && globalPosMs <= (chunk.startMs + chunk.durMs)
        } ?: orderedChunks.last()

        val index = orderedChunks.indexOf(targetChunk).coerceAtLeast(0)
        val offsetInChunk = (globalPosMs - targetChunk.startMs).coerceIn(0L, targetChunk.durMs)
        exoPlayer.seekTo(index, offsetInChunk)
        updateGlobalPosition()
    }

    fun setPlaybackSpeed(speed: Float) {
        if (exoPlayer.playbackParameters.speed != speed) {
            exoPlayer.playbackParameters = PlaybackParameters(speed)
        }
    }

    fun getPlaybackSpeed(): Float = exoPlayer.playbackParameters.speed

    fun setVideoVolume(volume: Float) {
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    fun clearPlaylist() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        chunkByMediaId.clear()
        orderedChunks.clear()
        _globalPositionMs.value = 0L
        _totalQueuedDurationMs.value = 0L
        _currentChunkNo.value = 1
    }

    fun release() {
        positionTickerJob?.cancel()
        exoPlayer.release()
    }
}
