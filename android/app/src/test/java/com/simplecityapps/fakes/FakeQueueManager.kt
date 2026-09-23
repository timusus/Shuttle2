package com.simplecityapps.fakes

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.MutableStateFlow

class FakeQueueManager : QueueOperations {
    override val queueStateFlow = MutableStateFlow(QueueState.Empty)
    override val shuffleModeFlow = MutableStateFlow(QueueManager.ShuffleMode.Off)
    override val repeatModeFlow = MutableStateFlow(QueueManager.RepeatMode.Off)

    override var hasRestoredQueue: Boolean = false

    var setQueueResult: Boolean = true

    var lastSetQueue: List<Song>? = null
        private set
    var lastSetQueuePosition: Int? = null
        private set

    var nextItem: QueueItem? = null

    override suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>?, position: Int): Boolean {
        lastSetQueue = songs
        lastSetQueuePosition = position
        return setQueueResult
    }
    override fun getQueue(): List<QueueItem> = queueStateFlow.value.items
    override fun getQueue(shuffleMode: QueueManager.ShuffleMode): List<QueueItem> = queueStateFlow.value.items
    override fun getCurrentItem(): QueueItem? = queueStateFlow.value.currentItem
    override fun getCurrentPosition(): Int? = queueStateFlow.value.currentPosition
    override fun getSize(): Int = queueStateFlow.value.items.size
    override fun setCurrentItem(currentItem: QueueItem) {}
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = nextItem
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
    override fun getShuffleMode(): QueueManager.ShuffleMode = shuffleModeFlow.value
    override suspend fun setShuffleMode(shuffleMode: QueueManager.ShuffleMode, reshuffle: Boolean) {}
    override suspend fun toggleShuffleMode() {}
    override fun getRepeatMode(): QueueManager.RepeatMode = repeatModeFlow.value
    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {}
    override fun toggleRepeatMode() {}
}
