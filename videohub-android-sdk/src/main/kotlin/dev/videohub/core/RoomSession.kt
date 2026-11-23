// Internal engine imports — not exposed in public API
package dev.videohub.core

import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.RemoteParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Represents an active room session.
 *
 * Matches the official VideoHub SDK's RoomSession API.
 * Wraps VideoHub Room internally — internal engine NOT exposed.
 */
class RoomSession(
    private val room: Room,
    private val scope: CoroutineScope,
) {
    /** Local participant — controls mic/camera */
    val local: LocalParticipant get() = room.localParticipant

    /** Remote participants in the room */
    val remoteParticipants: Map<String, RemoteParticipant> get() = room.remoteParticipants

    // Event flow — matches session.observeEvents()
    private val _events = MutableSharedFlow<VideoHubEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VideoHubEvent> = _events.asSharedFlow()

    // Active speakers flow — matches session.activeSpeakers
    private val _activeSpeakers = MutableStateFlow<List<String>>(emptyList())
    val activeSpeakers: StateFlow<List<String>> = _activeSpeakers.asStateFlow()

    private var eventJob: Job? = null

    init {
        observeRoomEvents()
    }

    /** Observe VideoHub room events and re-emit as VideoHubEvent */
    private fun observeRoomEvents() {
        eventJob = scope.launch {
            try {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.ParticipantConnected -> {
                            _events.emit(VideoHubEvent.ParticipantJoined(event.participant.identity ?: ""))
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            _events.emit(VideoHubEvent.ParticipantLeft(event.participant.identity ?: ""))
                        }
                        is RoomEvent.ActiveSpeakerChanged -> {
                            val speakers = event.speakers.mapNotNull { it.identity?.toString() }
                            _activeSpeakers.value = speakers
                            _events.emit(VideoHubEvent.ActiveSpeakerChanged(speakers))
                        }
                        is RoomEvent.TrackPublished -> {
                            _events.emit(VideoHubEvent.TrackPublished(
                                participantId = event.participant.identity ?: "",
                                trackId = event.publication.sid,
                                kind = event.publication.kind.name,
                            ))
                        }
                        is RoomEvent.TrackUnpublished -> {
                            _events.emit(VideoHubEvent.TrackUnpublished(
                                participantId = event.participant.identity ?: "",
                                trackId = event.publication.sid,
                            ))
                        }
                        is RoomEvent.Reconnecting -> {
                            _events.emit(VideoHubEvent.Reconnecting)
                        }
                        is RoomEvent.Reconnected -> {
                            _events.emit(VideoHubEvent.Reconnected)
                        }
                        is RoomEvent.Disconnected -> {
                            _events.emit(VideoHubEvent.Disconnected(event.reason))
                        }
                        else -> {}
                    }
                }
            } catch (_: Throwable) {
                // Session ended
            }
        }
    }

    /** Observe events as Flow — matches official SDK's session.observeEvents() */
    fun observeEvents(): SharedFlow<VideoHubEvent> = events

    /** Enable/disable microphone — matches session.local.setMicrophoneEnabled() */
    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        local.setMicrophoneEnabled(enabled)
    }

    /** Enable/disable camera */
    suspend fun setCameraEnabled(enabled: Boolean) {
        local.setCameraEnabled(enabled)
    }

    /** Publish audio stream with microphone — matches session.local.publishAudioStream() */
    suspend fun publishAudioStream(withMicrophone: Boolean = true) {
        if (withMicrophone) {
            local.setMicrophoneEnabled(true)
        }
    }

    /** Publish screen share */
    suspend fun startScreenShare() {
        local.setScreenShareEnabled(true)
    }

    /** Stop screen share */
    suspend fun stopScreenShare() {
        local.setScreenShareEnabled(false)
    }

    /** Send data to all participants via VideoHub data channel */
    suspend fun sendData(data: ByteArray, reliable: Boolean = true) {
        local.publishData(data, reliable = reliable)
    }

    /** Disconnect from the room */
    fun disconnect() {
        eventJob?.cancel()
        room.disconnect()
    }
}
