package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Callable from any thread. A call that changes the queue runs on the main thread, where the player lives: a suspend
 * call switches to it, and any other call made off it is posted to it, so its effect isn't visible until the main
 * thread gets to it. Reads return the last published state. [PlaybackOperations][com.simplecityapps.playback.PlaybackOperations]
 * follows the same rule.
 */
interface QueueOperations {
    /** The queue as the active shuffle mode presents it, with the current item and position. */
    val queueStateFlow: StateFlow<QueueState>
    val shuffleModeFlow: StateFlow<QueueManager.ShuffleMode>
    val repeatModeFlow: StateFlow<QueueManager.RepeatMode>

    suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>? = null, position: Int = 0): Boolean

    /** Builds a queue for [setQueueIfContentVersion], off the main thread: a long queue takes a while to build. */
    suspend fun buildQueue(songs: List<Song>, shuffleSongs: List<Song>?, position: Int): NewQueue

    /**
     * Sets [queue] as [setQueue] does, only if the queue's [QueueState.contentVersion] is still [contentVersion]. Main
     * thread only, so the check and the set are one step, which no other change can come between, and a caller can
     * do more in that same step.
     *
     * @return the content version the queue is left at, or null if it had changed and was left alone.
     */
    fun setQueueIfContentVersion(contentVersion: Long, queue: NewQueue): Long?

    fun getQueue(): List<QueueItem>
    fun getQueue(shuffleMode: QueueManager.ShuffleMode): List<QueueItem>
    fun getCurrentItem(): QueueItem?
    fun getCurrentPosition(): Int?
    fun getSize(): Int
    fun setCurrentItem(currentItem: QueueItem)
    fun getNext(ignoreRepeat: Boolean = false): QueueItem?
    fun getPrevious(): QueueItem?
    fun skipToNext(ignoreRepeat: Boolean = false): Boolean
    fun skipToPrevious()
    fun skipTo(position: Int)

    /** Adds [songs] to the end of the queue. Added to an empty queue, they're set as a new queue, and this returns true. */
    suspend fun addToQueue(songs: List<Song>): Boolean

    /** Adds [songs] after the current item. Added to an empty queue, they're set as a new queue, and this returns true. */
    suspend fun addToNext(songs: List<Song>): Boolean

    /**
     * Replaces the song data of any queue item whose song id matches one of [songs], preserving the
     * item's uid, position and current status. Publishes [QueueState.songDataVersion] rather than
     * [QueueState.contentVersion], so this doesn't trigger a reload of the current item.
     */
    fun updateSongs(songs: List<Song>)
    fun move(from: Int, to: Int)
    fun remove(items: List<QueueItem>)
    fun remove(song: Song)
    fun clear()
    fun getShuffleMode(): QueueManager.ShuffleMode
    suspend fun setShuffleMode(shuffleMode: QueueManager.ShuffleMode, reshuffle: Boolean)
    suspend fun toggleShuffleMode()
    fun getRepeatMode(): QueueManager.RepeatMode
    fun setRepeatMode(repeatMode: QueueManager.RepeatMode)
    fun toggleRepeatMode()
    var hasRestoredQueue: Boolean
}
