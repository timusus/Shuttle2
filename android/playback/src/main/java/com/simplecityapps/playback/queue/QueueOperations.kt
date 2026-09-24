package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.StateFlow

interface QueueOperations {
    /** The queue as the active shuffle mode presents it, with the current item and position. */
    val queueStateFlow: StateFlow<QueueState>
    val shuffleModeFlow: StateFlow<QueueManager.ShuffleMode>
    val repeatModeFlow: StateFlow<QueueManager.RepeatMode>

    suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>? = null, position: Int = 0): Boolean

    /**
     * [setQueue], only if the queue's [QueueState.contentVersion] is still [contentVersion]: the check and the set
     * are one step, which no other [setQueue] can come between.
     *
     * @return the content version the queue is left at, or null if it had changed and was left alone.
     */
    suspend fun setQueueIfContentVersion(contentVersion: Long, songs: List<Song>, shuffleSongs: List<Song>?, position: Int): Long?
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
    fun addToQueue(songs: List<Song>)
    fun addToNext(songs: List<Song>)

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
