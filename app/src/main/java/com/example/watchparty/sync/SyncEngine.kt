package com.example.watchparty.sync

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.watchparty.media.ChunkInfo
import com.example.watchparty.media.ChunkPlayer
import com.example.watchparty.media.ChunkReceiver
import com.example.watchparty.media.ChunkSender
import com.example.watchparty.media.ChunkStore
import com.example.watchparty.media.Chunker
import com.example.watchparty.media.ReceiverEvent
import com.example.watchparty.net.ClientConnection
import com.example.watchparty.net.HostServer
import com.example.watchparty.net.SlowUserDto
import com.example.watchparty.net.WsMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

enum class PartySyncState {
    IDLE,
    LOADING,
    READY_WAIT,
    PLAYING,
    BUFFERING,
    PAUSED,
    ENDED
}

/**
 * SyncEngine (sync/SyncEngine.kt)
 * Coordinates background media streaming, mid-party late-joiner catch-up,
 * READY barriers, waiting banners, play_at countdown + 20ms busy-wait precision start,
 * 5s drift correction, pause/resume, and seek.
 */
class SyncEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    val isHost: Boolean,
    val chunkStore: ChunkStore,
    private val chunker: Chunker,
    private val hostServer: HostServer?,
    private val clientConnection: ClientConnection?
) {
    private val tag = "SyncEngine"

    val clockSync = ClockSync(
        isHost = isHost,
        clientConnection = clientConnection,
        scope = scope
    )

    val chunkPlayer = ChunkPlayer(
        context = context,
        scope = scope,
        onChunkTransitioned = { prevChunkNo, newChunkNo, gapMs ->
            handleChunkTransition(prevChunkNo, newChunkNo, gapMs)
        },
        onPlaybackEnded = {
            handlePlaybackEnded()
        }
    )

    private val chunkSender: ChunkSender? = hostServer?.let { ChunkSender(it) }
    val chunkReceiver: ChunkReceiver? = clientConnection?.let { ChunkReceiver(chunkStore, it) }

    private val _syncState = MutableStateFlow(PartySyncState.IDLE)
    val syncState: StateFlow<PartySyncState> = _syncState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _countdownNumber = MutableStateFlow<Int?>(null)
    val countdownNumber: StateFlow<Int?> = _countdownNumber.asStateFlow()

    private val _driftMs = MutableStateFlow(0L)
    val driftMs: StateFlow<Long> = _driftMs.asStateFlow()

    private val _canStartAnyway = MutableStateFlow(false)
    val canStartAnyway: StateFlow<Boolean> = _canStartAnyway.asStateFlow()

    private val hostChunksByNo = ConcurrentHashMap<Int, ChunkInfo>()
    private val chunkDoneUsersByChunk = ConcurrentHashMap<Int, MutableSet<String>>()
    private val chunkFreeGate = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
    private val lateJoinersAwaitingFirstSync = ConcurrentHashMap.newKeySet<String>()

    private var forceStartAnyway = false
    private var allChunksGenerated = false

    private var pipelineJob: Job? = null
    private var syncBroadcastJob: Job? = null
    private var preEndBufferWatchJob: Job? = null
    private var scheduledPlayJob: Job? = null

    init {
        if (isHost && hostServer != null) {
            observeHostEvents(hostServer)
        } else if (!isHost && clientConnection != null && chunkReceiver != null) {
            observeParticipantEvents(clientConnection, chunkReceiver)
        }
    }

    private fun observeHostEvents(server: HostServer) {
        scope.launch {
            server.clientEvents.collect { evt ->
                when (val msg = evt.message) {
                    is WsMessage.Join -> {
                        // Support mid-party late joiners seamlessly!
                        if (_syncState.value == PartySyncState.PLAYING || _syncState.value == PartySyncState.PAUSED) {
                            lateJoinersAwaitingFirstSync.add(evt.userId)
                            val activeChunkNo = chunkPlayer.currentChunkNo.value
                            val currentChunk = hostChunksByNo[activeChunkNo] ?: hostChunksByNo.values.minByOrNull { it.chunkNo }
                            val nextChunk = hostChunksByNo[activeChunkNo + 1]
                            launch {
                                if (currentChunk != null) {
                                    chunkSender?.sendChunkToUser(evt.userId, currentChunk)
                                }
                                if (nextChunk != null) {
                                    chunkSender?.sendChunkToUser(evt.userId, nextChunk)
                                }
                            }
                        }
                    }
                    is WsMessage.Ready -> {
                        if (lateJoinersAwaitingFirstSync.remove(evt.userId)) {
                            val currentPos = chunkPlayer.globalPositionMs.value
                            if (_syncState.value == PartySyncState.PLAYING) {
                                val startAt = SystemClock.elapsedRealtime() + 600L
                                server.sendTextToUser(
                                    evt.userId,
                                    WsMessage.PlayAt(
                                        chunkNo = msg.chunkNo,
                                        atHostMs = startAt,
                                        posMs = currentPos
                                    )
                                )
                            } else if (_syncState.value == PartySyncState.PAUSED) {
                                server.sendTextToUser(
                                    evt.userId,
                                    WsMessage.Pause(posMs = currentPos)
                                )
                            }
                        }
                    }
                    is WsMessage.ChunkDone -> {
                        val set = chunkDoneUsersByChunk.getOrPut(msg.chunkNo) { ConcurrentHashMap.newKeySet() }
                        set.add(evt.userId)
                        checkHostChunkCleanup(msg.chunkNo)
                    }
                    is WsMessage.Resend -> {
                        val chunk = hostChunksByNo[msg.chunkNo]
                        if (chunk != null) {
                            launch { chunkSender?.sendChunkToUser(evt.userId, chunk) }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeParticipantEvents(client: ClientConnection, receiver: ChunkReceiver) {
        clockSync.startSyncLoop()

        // Monitor connection drops and auto-reconnect recovery
        scope.launch {
            client.status.collect { status ->
                when (status) {
                    is com.example.watchparty.net.ConnectionStatus.Reconnecting -> {
                        withContext(Dispatchers.Main) {
                            if (chunkPlayer.isPlaying.value) {
                                chunkPlayer.pause()
                            }
                            _statusBanner.value =
                                "WiFi Glitch! Auto-Reconnecting (${status.attempt}/${status.maxAttempts})... 🔄"
                        }
                    }
                    is com.example.watchparty.net.ConnectionStatus.Connected -> {
                        if (status.isReconnected) {
                            withContext(Dispatchers.Main) {
                                _statusBanner.value = "Reconnected! Resyncing playback... ✓"
                            }
                            // Trigger rapid clock sync and notify Host of our latest ready chunk
                            repeat(3) {
                                client.send(WsMessage.TimeReq(t0 = SystemClock.elapsedRealtime()))
                                delay(120L)
                            }
                            val currentReadyChunk = chunkPlayer.currentChunkNo.value.coerceAtLeast(1)
                            client.send(WsMessage.Ready(chunkNo = currentReadyChunk))
                        }
                    }
                    is com.example.watchparty.net.ConnectionStatus.Error -> {
                        if (_syncState.value != PartySyncState.IDLE && _syncState.value != PartySyncState.ENDED) {
                            _statusBanner.value = status.message
                        }
                    }
                    else -> {}
                }
            }
        }

        scope.launch {
            receiver.downloadProgress.collect { pair ->
                if (pair != null) {
                    val (chunkNo, pct) = pair
                    if (chunkNo == 1 && (_syncState.value == PartySyncState.IDLE || _syncState.value == PartySyncState.LOADING)) {
                        _syncState.value = PartySyncState.LOADING
                        _statusBanner.value = "Syncing video stream... $pct%"
                    } else if (_syncState.value == PartySyncState.BUFFERING) {
                        _statusBanner.value = "Buffering stream... $pct%"
                    }
                }
            }
        }

        scope.launch {
            receiver.events.collect { event ->
                when (event) {
                    is ReceiverEvent.ChunkVerified -> {
                        withContext(Dispatchers.Main) {
                            chunkPlayer.enqueueChunk(event.chunk)
                            if (event.chunk.chunkNo == 1 && _syncState.value == PartySyncState.LOADING) {
                                _syncState.value = PartySyncState.READY_WAIT
                                _statusBanner.value = "Ready ✓ Waiting for friends..."
                            } else {
                                _statusBanner.value = null
                            }
                        }
                    }
                    is ReceiverEvent.ShaMismatchRetry -> {
                        if (_syncState.value != PartySyncState.PLAYING) {
                            _statusBanner.value = "Re-syncing stream (${event.retryAttempt}/2)..."
                        }
                    }
                    is ReceiverEvent.FatalError -> {
                        _statusBanner.value = "Stream connection interrupted"
                    }
                }
            }
        }

        scope.launch {
            client.binaryFrames.collect { frame ->
                receiver.onBinaryFrame(frame)
            }
        }

        scope.launch {
            client.textMessages.collect { msg ->
                when (msg) {
                    is WsMessage.TimeRes -> clockSync.onTimeResponse(msg)
                    is WsMessage.ChunkMeta -> receiver.onChunkMeta(msg)
                    is WsMessage.Waiting -> {
                        if (msg.names.isNotEmpty() && _syncState.value != PartySyncState.PLAYING) {
                            val summary = msg.names.joinToString(", ") { it.name }
                            _statusBanner.value = "Syncing with $summary..."
                        }
                    }
                    is WsMessage.PlayAt -> {
                        scheduleSynchronizedPlay(
                            atHostMs = msg.atHostMs,
                            posMs = msg.posMs,
                            showCountdown = true
                        )
                    }
                    is WsMessage.Pause -> {
                        scheduledPlayJob?.cancel()
                        withContext(Dispatchers.Main) {
                            chunkPlayer.pause()
                            chunkPlayer.seekToGlobalPosition(msg.posMs)
                            chunkPlayer.setPlaybackSpeed(1.0f)
                            _syncState.value = PartySyncState.PAUSED
                            _statusBanner.value = "Paused by Host"
                        }
                    }
                    is WsMessage.Resume -> {
                        scheduleSynchronizedPlay(
                            atHostMs = msg.atHostMs,
                            posMs = msg.posMs,
                            showCountdown = false
                        )
                    }
                    is WsMessage.Sync -> {
                        handleDriftSync(msg.posMs, msg.hostMs)
                    }
                    is WsMessage.Seek -> {
                        withContext(Dispatchers.Main) {
                            chunkPlayer.clearPlaylist()
                            receiver.reset()
                            _syncState.value = PartySyncState.LOADING
                            _statusBanner.value = "Syncing to new position..."
                        }
                    }
                    is WsMessage.Ended -> {
                        withContext(Dispatchers.Main) {
                            _syncState.value = PartySyncState.ENDED
                            chunkPlayer.clearPlaylist()
                            chunkStore.clearAll()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun startHostPipeline(
        videoUri: Uri,
        chunkLengthSec: Int,
        startFromMs: Long = 0L
    ) {
        if (!isHost || hostServer == null || chunkSender == null) return
        pipelineJob?.cancel()
        syncBroadcastJob?.cancel()
        preEndBufferWatchJob?.cancel()

        val firstDurMs = 10_000L
        val nextDurMs = chunkLengthSec * 1000L

        allChunksGenerated = false
        forceStartAnyway = false
        _syncState.value = PartySyncState.LOADING
        _statusBanner.value = "Preparing HD stream..."

        pipelineJob = scope.launch {
            try {
                chunker.chunkVideoFlow(
                    uri = videoUri,
                    firstChunkDurationMs = firstDurMs,
                    subsequentChunkDurationMs = nextDurMs,
                    startFromMs = startFromMs,
                    startingChunkNo = 1
                ).collect { chunk ->
                    hostChunksByNo[chunk.chunkNo] = chunk
                    withContext(Dispatchers.Main) {
                        chunkPlayer.enqueueChunk(chunk)
                    }
                    hostServer.updateHostReadyChunk(chunk.chunkNo)

                    if (chunk.chunkNo == 1) {
                        _statusBanner.value = "Syncing with friends..."
                    }
                    chunkSender.broadcastChunk(chunk)

                    if (chunk.chunkNo == 1) {
                        waitForChunk1ReadyAndPlay(startFromMs)
                    } else if (_syncState.value == PartySyncState.PLAYING) {
                        _statusBanner.value = null
                    }

                    while (isActive && (chunk.chunkNo - chunkPlayer.currentChunkNo.value) >= 2) {
                        delay(250L)
                    }
                }
                allChunksGenerated = true
            } catch (e: Exception) {
                Log.e(tag, "Host pipeline error: ${e.message}", e)
                _statusBanner.value = e.localizedMessage ?: "Video stream error"
            }
        }

        startHostPeriodicSyncAndWatchdog()
    }

    private suspend fun waitForChunk1ReadyAndPlay(initialPosMs: Long) {
        val server = hostServer ?: return
        _syncState.value = PartySyncState.READY_WAIT
        _canStartAnyway.value = true

        while (scope.isActive && !forceStartAnyway) {
            val nonHost = server.participants.value.filter { !it.isHost }
            val slowUsers = nonHost.filter { it.readyChunk < 1 }.map {
                SlowUserDto(name = it.name, percent = it.downloadPercent)
            }
            if (slowUsers.isEmpty()) break

            val summary = slowUsers.joinToString(", ") { it.name }
            _statusBanner.value = "Syncing with $summary..."
            server.broadcastText(WsMessage.Waiting(names = slowUsers))
            delay(1000L)
        }

        _canStartAnyway.value = false
        val atHostMs = SystemClock.elapsedRealtime() + 2000L
        server.broadcastText(
            WsMessage.PlayAt(
                chunkNo = 1,
                atHostMs = atHostMs,
                posMs = initialPosMs
            )
        )
        scheduleSynchronizedPlay(atHostMs = atHostMs, posMs = initialPosMs, showCountdown = true)
    }

    fun triggerStartAnyway() {
        forceStartAnyway = true
    }

    private fun startHostPeriodicSyncAndWatchdog() {
        val server = hostServer ?: return

        syncBroadcastJob = scope.launch {
            while (isActive) {
                delay(5000L)
                if (_syncState.value == PartySyncState.PLAYING && chunkPlayer.isPlaying.value) {
                    server.broadcastText(
                        WsMessage.Sync(
                            posMs = chunkPlayer.globalPositionMs.value,
                            hostMs = SystemClock.elapsedRealtime()
                        )
                    )
                }
            }
        }

        preEndBufferWatchJob = scope.launch {
            while (isActive) {
                delay(400L)
                if (_syncState.value != PartySyncState.PLAYING) continue
                val currentNo = chunkPlayer.currentChunkNo.value
                val currentChunk = hostChunksByNo[currentNo] ?: continue
                val nextNo = currentNo + 1
                val nextChunkExists = hostChunksByNo.containsKey(nextNo) || !allChunksGenerated
                if (!nextChunkExists) continue

                val elapsedInChunk = chunkPlayer.globalPositionMs.value - currentChunk.startMs
                val remainingMs = currentChunk.durMs - elapsedInChunk

                if (remainingMs in 1L..3000L && hostChunksByNo.containsKey(nextNo)) {
                    val nonHost = server.participants.value.filter { !it.isHost }
                    val notReady = nonHost.filter { it.readyChunk < nextNo }
                    if (notReady.isNotEmpty()) {
                        val pausePos = chunkPlayer.globalPositionMs.value
                        withContext(Dispatchers.Main) { chunkPlayer.pause() }
                        _syncState.value = PartySyncState.BUFFERING
                        server.broadcastText(WsMessage.Pause(posMs = pausePos))

                        while (isActive) {
                            val currentSlow = server.participants.value
                                .filter { !it.isHost && it.readyChunk < nextNo }
                                .map { SlowUserDto(it.name, it.downloadPercent) }
                            if (currentSlow.isEmpty()) break
                            val summary = currentSlow.joinToString(", ") { it.name }
                            _statusBanner.value = "Buffering: Waiting for $summary..."
                            server.broadcastText(WsMessage.Waiting(names = currentSlow))
                            delay(1000L)
                        }

                        val resumeAt = SystemClock.elapsedRealtime() + 1500L
                        server.broadcastText(WsMessage.Resume(atHostMs = resumeAt, posMs = pausePos))
                        scheduleSynchronizedPlay(atHostMs = resumeAt, posMs = pausePos, showCountdown = false)
                    }
                }
            }
        }
    }

    private fun scheduleSynchronizedPlay(
        atHostMs: Long,
        posMs: Long,
        showCountdown: Boolean
    ) {
        scheduledPlayJob?.cancel()
        scheduledPlayJob = scope.launch {
            val localTargetMs = clockSync.toLocal(atHostMs)
            withContext(Dispatchers.Main) {
                chunkPlayer.pause()
                chunkPlayer.setPlaybackSpeed(1.0f)
                if (posMs > 0L) {
                    chunkPlayer.seekToGlobalPosition(posMs)
                }
            }

            if (showCountdown) {
                _statusBanner.value = "Everyone Ready! Starting..."
                val c3 = (localTargetMs - SystemClock.elapsedRealtime())
                if (c3 > 1300L) {
                    _countdownNumber.value = 3
                    delay(650L)
                }
                if (localTargetMs - SystemClock.elapsedRealtime() > 650L) {
                    _countdownNumber.value = 2
                    delay(650L)
                }
                if (localTargetMs - SystemClock.elapsedRealtime() > 100L) {
                    _countdownNumber.value = 1
                }
            } else {
                _statusBanner.value = null
            }

            val remainingDelay = localTargetMs - SystemClock.elapsedRealtime()
            if (remainingDelay > 20L) {
                delay(remainingDelay - 20L)
            }

            if (remainingDelay > 0L) {
                while (SystemClock.elapsedRealtime() < localTargetMs) {
                }
                withContext(Dispatchers.Main) {
                    _countdownNumber.value = null
                    _statusBanner.value = null
                    _syncState.value = PartySyncState.PLAYING
                    chunkPlayer.play()
                }
            } else {
                val catchUpPos = posMs + (-remainingDelay)
                withContext(Dispatchers.Main) {
                    _countdownNumber.value = null
                    _statusBanner.value = null
                    _syncState.value = PartySyncState.PLAYING
                    chunkPlayer.seekToGlobalPosition(catchUpPos)
                    chunkPlayer.play()
                }
            }
        }
    }

    private suspend fun handleDriftSync(hostPosMs: Long, sentAtHostMs: Long) {
        if (isHost || _syncState.value != PartySyncState.PLAYING) return
        withContext(Dispatchers.Main) {
            if (!chunkPlayer.isPlaying.value) return@withContext
            val nowHostMs = clockSync.toHostTime(SystemClock.elapsedRealtime())
            val transitMs = (nowHostMs - sentAtHostMs).coerceAtLeast(0L)
            val expectedPosMs = hostPosMs + transitMs
            val currentPosMs = chunkPlayer.globalPositionMs.value
            val diffMs = currentPosMs - expectedPosMs
            _driftMs.value = diffMs
            val absDiff = abs(diffMs)

            when {
                absDiff < 40L -> {
                    if (chunkPlayer.getPlaybackSpeed() != 1.0f) {
                        chunkPlayer.setPlaybackSpeed(1.0f)
                        _statusBanner.value = null
                    }
                }
                absDiff < 100L -> {
                }
                absDiff <= 500L -> {
                    val targetSpeed = if (diffMs > 0) 0.97f else 1.03f
                    chunkPlayer.setPlaybackSpeed(targetSpeed)
                }
                else -> {
                    chunkPlayer.setPlaybackSpeed(1.0f)
                    chunkPlayer.seekToGlobalPosition(expectedPosMs)
                }
            }
        }
    }

    fun hostPause() {
        if (!isHost || hostServer == null) return
        scheduledPlayJob?.cancel()
        val pos = chunkPlayer.globalPositionMs.value
        chunkPlayer.pause()
        _syncState.value = PartySyncState.PAUSED
        _statusBanner.value = "Paused by Host"
        hostServer.broadcastText(WsMessage.Pause(posMs = pos))
    }

    fun hostResume() {
        if (!isHost || hostServer == null) return
        val pos = chunkPlayer.globalPositionMs.value
        val resumeAt = SystemClock.elapsedRealtime() + 1500L
        hostServer.broadcastText(WsMessage.Resume(atHostMs = resumeAt, posMs = pos))
        scheduleSynchronizedPlay(atHostMs = resumeAt, posMs = pos, showCountdown = false)
    }

    fun hostSeekTo(videoUri: Uri, chunkLengthSec: Int, targetPosMs: Long) {
        if (!isHost || hostServer == null) return
        scheduledPlayJob?.cancel()
        pipelineJob?.cancel()
        chunkPlayer.clearPlaylist()
        chunkStore.clearAll()
        hostChunksByNo.clear()
        chunkDoneUsersByChunk.clear()
        hostServer.broadcastText(WsMessage.Seek(targetPosMs = targetPosMs))
        startHostPipeline(videoUri = videoUri, chunkLengthSec = chunkLengthSec, startFromMs = targetPosMs)
    }

    private fun handleChunkTransition(completedChunkNo: Int, newChunkNo: Int, gapMs: Long) {
        if (isHost) {
            checkHostChunkCleanup(completedChunkNo)
        } else {
            chunkReceiver?.onChunkPlayedAndDeleted(completedChunkNo)
        }
    }

    private fun checkHostChunkCleanup(chunkNo: Int) {
        val server = hostServer ?: return
        val nonHostIds = server.participants.value.filter { !it.isHost }.map { it.userId }.toSet()
        val doneSet = chunkDoneUsersByChunk[chunkNo].orEmpty()
        val hostMovedPast = chunkPlayer.currentChunkNo.value > chunkNo
        if (hostMovedPast && doneSet.containsAll(nonHostIds)) {
            chunkStore.deleteHostChunk(chunkNo)
            hostChunksByNo.remove(chunkNo)
            chunkFreeGate.remove(chunkNo)?.complete(Unit)
        }
    }

    private fun handlePlaybackEnded() {
        if (isHost) {
            if (allChunksGenerated) {
                _syncState.value = PartySyncState.ENDED
                hostServer?.broadcastText(WsMessage.Ended())
                chunkStore.clearAll()
            }
        } else {
            if (_syncState.value == PartySyncState.PLAYING) {
                _syncState.value = PartySyncState.BUFFERING
                _statusBanner.value = "Buffering..."
            }
        }
    }

    fun cleanup() {
        scheduledPlayJob?.cancel()
        pipelineJob?.cancel()
        syncBroadcastJob?.cancel()
        preEndBufferWatchJob?.cancel()
        clockSync.stop()
        chunkPlayer.release()
        chunkStore.clearAll()
    }
}
