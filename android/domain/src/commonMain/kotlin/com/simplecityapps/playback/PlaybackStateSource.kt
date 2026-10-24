package com.simplecityapps.playback

import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A queue item that played to its end: [uid] says which copy, as the same [song] may be queued more than once. */
class TrackEnd(
    val uid: Long,
    val song: Song
)

/** One play of the queue item [uid]: its stream URLs and its playback reports to the server all carry [id]. */
data class Play(
    val id: String,
    val uid: Long
)

/** [song] failed to load; [serverUnreachable] when the cause was a connection error rather than the song itself. */
data class SongLoadFailure(
    val song: Song,
    val serverUnreachable: Boolean
)

/** The read side of playback: state, progress and events; commands are on [PlaybackOperations]. */
interface PlaybackStateSource {
    /** The player's playback state: loading until the current item is ready, then playing or paused. */
    val playbackStateFlow: StateFlow<PlaybackState>

    /** The last published progress; null until the first. Ticks while playing, and is republished on every jump (a seek or a track change). */
    val progressFlow: StateFlow<PlaybackProgress?>

    /** The playback speed, 1 being normal; republished each time it changes. */
    val playbackSpeedFlow: StateFlow<Float>

    /** The Cast device playback plays on, or null while it plays on this one; republished each time it moves. */
    val castDeviceFlow: StateFlow<CastDevice?>

    /** The current item's play, null with none: a new one for each copy of a song in the queue, and for each replay the player opens afresh. */
    val playFlow: StateFlow<Play?>

    /**
     * Each queue item that plays to its end, emitted before the queue moves on. An event, not state: nothing is
     * replayed to a new collector, and a collector on the main thread sees every one.
     */
    val trackEndedFlow: SharedFlow<TrackEnd>

    /**
     * The current song and the position playback paused at (0 if it has none), emitted each time playback
     * reports a pause, once that position has been saved as the one to resume from. An event, like
     * [trackEndedFlow].
     */
    val pausePositionFlow: SharedFlow<SongPosition>

    /** The current song, each time the playback fails to play it (e.g. its file can't be read). An event, like [trackEndedFlow]. */
    val playbackFailureFlow: SharedFlow<Song>

    /**
     * Each song that fails to load, and whether its server couldn't be reached. Unlike [playbackFailureFlow] it also
     * carries a song whose stream couldn't be resolved. An event, like [trackEndedFlow]. Nothing on a player that
     * doesn't tell the two apart.
     */
    val loadFailureFlow: SharedFlow<SongLoadFailure>
        get() = MutableSharedFlow()

    fun playbackState(): PlaybackState
    fun getProgress(): Int?
    fun getDuration(): Int?
    fun getPlaybackSpeed(): Float
}
