package com.simplecityapps.playback

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.Song

interface PlaybackOperations {
    fun load(seekPosition: Int? = null, completion: (Result<Boolean>) -> Unit)
    fun play(attempt: Int = 1)
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
    fun getPlayback(): Playback
    fun switchToPlayback(playback: Playback)
}
