package dev.videohub.core

/**
 * Events emitted by VideoHub room sessions.
 * Matches the official SDK's VideoHubEvent sealed class.
 */
sealed class VideoHubEvent {
    /** Room connected successfully */
    object Connected : VideoHubEvent()

    /** Disconnected from room */
    data class Disconnected(val reason: String? = null) : VideoHubEvent()

    /** Reconnecting to room */
    object Reconnecting : VideoHubEvent()

    /** Reconnected to room */
    object Reconnected : VideoHubEvent()

    /** A participant joined the room */
    data class ParticipantJoined(val identity: String) : VideoHubEvent()

    /** A participant left the room */
    data class ParticipantLeft(val identity: String) : VideoHubEvent()

    /** Active speaker changed */
    data class ActiveSpeakerChanged(val speakers: List<String>) : VideoHubEvent()

    /** Track published */
    data class TrackPublished(val participantId: String, val trackId: String, val kind: String) : VideoHubEvent()

    /** Track unpublished */
    data class TrackUnpublished(val participantId: String, val trackId: String) : VideoHubEvent()

    /** Error occurred */
    data class Error(val error: Throwable) : VideoHubEvent()

    /** Media published */
    data class MediaPublished(val tracks: List<String>) : VideoHubEvent()
}
