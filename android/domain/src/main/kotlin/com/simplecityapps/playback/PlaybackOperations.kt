package com.simplecityapps.playback

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Callable from any thread. A call that changes playback runs on the main thread, where the player lives: straight away
 * if made there, else posted to it, so its effect isn't visible until the main thread gets to it. A read made off the
 * main thread returns the last published state. [QueueOperations][com.simplecityapps.playback.queue.QueueOperations]
 * follows the same rule.
 */
interface PlaybackOperations {
    /** The player's playback state: loading until the current item is ready, then playing or paused. */
    val playbackStateFlow: StateFlow<PlaybackState>

    /** The last published progress; null until the first. Ticks while playing, and is republished on every jump (a seek or a track change). */
    val progressFlow: StateFlow<PlaybackProgress?>

    /** The playback speed, 1 being normal; republished each time it changes. */
    val playbackSpeedFlow: StateFlow<Float>

    /**
     * Each song that plays to its end, emitted before the queue moves on. An event, not state: nothing is
     * replayed to a new collector, and a collector on the main thread sees every one.
     */
    val trackEndedFlow: SharedFlow<Song>

    /**
     * The current song and the position playback paused at (0 if it has none), emitted each time playback
     * reports a pause, once that position has been saved as the one to resume from. An event, like
     * [trackEndedFlow].
     */
    val pausePositionFlow: SharedFlow<SongPosition>

    /** The current song, each time the playback fails to play it (e.g. its file can't be read). An event, like [trackEndedFlow]. */
    val playbackFailureFlow: SharedFlow<Song>

    /**
     * Loads the current item, paused. An item that can't load is skipped for the next one, unless [skipUnloadable] is
     * false (a restore): then it stays current, paused, until it's played.
     */
    fun load(seekPosition: Int? = null, skipUnloadable: Boolean = true, completion: (Result<Boolean>) -> Unit)
    fun play()
    fun pause()
    fun togglePlayback()
    fun skipToNext(ignoreRepeat: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null)
    fun skipToPrev(force: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null)
    fun skipTo(position: Int)
    suspend fun addToQueue(songs: List<Song>)
    suspend fun playNext(songs: List<Song>)
    suspend fun shuffle(songs: List<Song>, completion: (Result<Any?>) -> Unit)
    fun seekTo(position: Int)
    fun playbackState(): PlaybackState
    fun getProgress(): Int?
    fun getDuration(): Int?
    fun getPlaybackSpeed(): Float
    fun setPlaybackSpeed(multiplier: Float)
    fun moveQueueItem(from: Int, to: Int)
    fun removeQueueItem(queueItem: QueueItem)
    fun clearQueue()
}
