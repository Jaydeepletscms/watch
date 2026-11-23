package com.example.watchparty.sync

import android.os.SystemClock
import com.example.watchparty.net.ClientConnection
import com.example.watchparty.net.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ClockSample(
    val rttMs: Long,
    val offsetMs: Long
)

/**
 * Section 8: Clock sync (sync/ClockSync.kt)
 * Host's SystemClock.elapsedRealtime() is master.
 * Participant sends 8 time_req messages (200 ms apart), computes rtt = t1 - t0,
 * offset = hostMs + rtt/2 - t1, keeps the sample with minimum RTT, and repeats every 60s.
 */
class ClockSync(
    private val isHost: Boolean,
    private val clientConnection: ClientConnection?,
    private val scope: CoroutineScope
) {
    private val _offsetMs = MutableStateFlow(0L)
    val offsetMs: StateFlow<Long> = _offsetMs.asStateFlow()

    private val _bestRttMs = MutableStateFlow(0L)
    val bestRttMs: StateFlow<Long> = _bestRttMs.asStateFlow()

    private var minRttObserved = Long.MAX_VALUE
    private var periodicSyncJob: Job? = null

    fun startSyncLoop() {
        if (isHost) {
            _offsetMs.value = 0L
            _bestRttMs.value = 0L
            return
        }
        periodicSyncJob?.cancel()
        periodicSyncJob = scope.launch {
            while (isActive) {
                minRttObserved = Long.MAX_VALUE
                repeat(8) {
                    val t0 = SystemClock.elapsedRealtime()
                    clientConnection?.send(WsMessage.TimeReq(t0 = t0))
                    delay(200L)
                }
                // Section 8.5: Repeat every 60 seconds
                delay(60_000L)
            }
        }
    }

    fun onTimeResponse(res: WsMessage.TimeRes) {
        if (isHost) return
        val t1 = SystemClock.elapsedRealtime()
        val rtt = (t1 - res.t0).coerceAtLeast(0L)
        val offset = res.hostMs + (rtt / 2L) - t1
        if (rtt < minRttObserved) {
            minRttObserved = rtt
            _offsetMs.value = offset
            _bestRttMs.value = rtt
        }
    }

    /**
     * Section 8.6: toLocal(hostMs) = hostMs - offset
     */
    fun toLocal(hostMs: Long): Long {
        return if (isHost) hostMs else hostMs - _offsetMs.value
    }

    fun toHostTime(localMs: Long = SystemClock.elapsedRealtime()): Long {
        return if (isHost) localMs else localMs + _offsetMs.value
    }

    fun stop() {
        periodicSyncJob?.cancel()
    }
}
