package com.simplecityapps.playback

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song

/**
 * Callable from any thread. A call that changes playback runs on the main thread, where the player lives: straight away
 * if made there, else posted to it, so its effect isn't visible until the main thread gets to it. A read made off the
 * main thread returns the last published state. [QueueOperations][com.simplecityapps.playback.queue.QueueOperations]
 * follows the same rule.
 */
interface PlaybackOperations {
    /**
     * Loads the current item, paused, or playing once it's ready if [playWhenReady]: a play that loads in one go, rather
     * than a load and then a play once it's ready, so the player can get its output ready while the item opens (#687).
     * An item that can't load is skipped for the next one, unless [skipUnloadable] is false (a restore): then it stays
     * current, paused, until it's played. A load replaced by another before its item was ready (a later load, or a skip)
     * fails with a [kotlinx.coroutines.CancellationException]: it didn't fail, it was dropped, and nothing should come
     * of it.
     */
    fun load(
        seekPosition: Int? = null,
        skipUnloadable: Boolean = true,
        playWhenReady: Boolean = false,
        completion: (Result<Boolean>) -> Unit
    )
    fun play()
    fun pause()

    /**
     * Lets the current item play to its end and pauses there, rather than moving on to the next one, then returns: the
     * pause comes before the next item starts, which [trackEndedFlow] (emitted as the queue moves on, after a gapless
     * start) can't give. An item skipped to before then plays to its end instead. Cancelling it lets playback move on
     * as before.
     */
    suspend fun pauseAtEndOfItem()
    fun togglePlayback()
    fun skipToNext(ignoreRepeat: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null)
    fun skipToPrev(force: Boolean = false, completion: ((Result<Any?>) -> Unit)? = null)
    fun skipTo(position: Int)
    suspend fun addToQueue(songs: List<Song>)
    suspend fun playNext(songs: List<Song>)
    suspend fun shuffle(
        songs: List<Song>,
        context: PlayContext = PlayContext.None,
        completion: (Result<Any?>) -> Unit
    )
    fun seekTo(position: Int)
    fun setPlaybackSpeed(multiplier: Float)
    fun moveQueueItem(from: Int, to: Int)
    fun removeQueueItem(queueItem: QueueItem)
    fun clearQueue()
}
