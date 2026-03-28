package com.simplecityapps.fakes

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song

class FakeQueueManager : QueueOperations {
    override var hasRestoredQueue: Boolean = false

    var setQueueResult: Boolean = true

    override suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>?, position: Int): Boolean = setQueueResult
    override fun getQueue(): List<QueueItem> = emptyList()
    override fun getQueue(shuffleMode: QueueManager.ShuffleMode): List<QueueItem> = emptyList()
    override fun getCurrentItem(): QueueItem? = null
    override fun getCurrentPosition(): Int? = null
    override fun getSize(): Int = 0
    override fun setCurrentItem(currentItem: QueueItem) {}
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = null
    override fun getPrevious(): QueueItem? = null
    override fun skipToNext(ignoreRepeat: Boolean): Boolean = false
    override fun skipToPrevious() {}
    override fun skipTo(position: Int) {}
    override fun addToQueue(songs: List<Song>) {}
    override fun addToNext(songs: List<Song>) {}
    override fun move(from: Int, to: Int) {}
    override fun remove(items: List<QueueItem>) {}
    override fun remove(song: Song) {}
    override fun clear() {}
    override fun getShuffleMode(): QueueManager.ShuffleMode = QueueManager.ShuffleMode.Off
    override suspend fun setShuffleMode(shuffleMode: QueueManager.ShuffleMode, reshuffle: Boolean) {}
    override suspend fun toggleShuffleMode() {}
    override fun getRepeatMode(): QueueManager.RepeatMode = QueueManager.RepeatMode.Off
    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {}
    override fun toggleRepeatMode() {}
}
