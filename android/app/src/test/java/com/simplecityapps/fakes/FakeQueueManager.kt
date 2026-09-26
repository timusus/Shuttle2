package com.simplecityapps.fakes

import com.simplecityapps.playback.queue.NewQueue
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.MutableStateFlow

class FakeQueueManager : QueueOperations {
    override val queueStateFlow = MutableStateFlow(QueueState.Empty)
    override val shuffleModeFlow = MutableStateFlow(ShuffleMode.Off)
    override val repeatModeFlow = MutableStateFlow(RepeatMode.Off)

    override var hasRestoredQueue: Boolean = false

    var setQueueResult: Boolean = true

    var lastSetQueue: List<Song>? = null
        private set
    var lastSetQueuePosition: Int? = null
        private set
    var lastSetShuffleQueue: List<Song>? = null
        private set

    /** The shuffle mode the last [setQueueIfContentVersion] set its queue with. */
    var lastSetQueueShuffleMode: ShuffleMode? = null
        private set

    var nextItem: QueueItem? = null

    override suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>?, position: Int): Boolean {
        lastSetQueue = songs
        lastSetShuffleQueue = shuffleSongs
        lastSetQueuePosition = position
        return setQueueResult
    }

    /** Whether [setQueueIfContentVersion] publishes the queue it sets, as the real one does. */
    var publishesRestoredQueue = false

    /** How many times the queue has been read for a shuffle mode: what saving it takes. */
    var shuffleModeQueueReads = 0
        private set

    /**
     * Leaves [queueStateFlow] as it is, so the content version a set leaves is the one it was set at, unless it
     * [publishesRestoredQueue].
     */
    override suspend fun buildQueue(songs: List<Song>, shuffleSongs: List<Song>?, position: Int): NewQueue = NewQueue.build(songs, shuffleSongs, position).also { buildThreads += Thread.currentThread() }

    /** The thread each [buildQueue] ran on. */
    val buildThreads = mutableListOf<Thread>()

    /** The thread each [setQueueIfContentVersion] ran on. */
    val setQueueThreads = mutableListOf<Thread>()

    override fun setQueueIfContentVersion(contentVersion: Long, queue: NewQueue, shuffleMode: ShuffleMode): Long? {
        setQueueThreads += Thread.currentThread()
        val previous = queueStateFlow.value
        if (previous.contentVersion != contentVersion) return null
        val songs = queue.songs
        val position = queue.position
        lastSetQueue = songs
        lastSetShuffleQueue = queue.shuffleSongs
        lastSetQueuePosition = position
        lastSetQueueShuffleMode = shuffleMode
        if (!publishesRestoredQueue) return contentVersion
        val items = songs.mapIndexed { index, song -> song.toQueueItem(isCurrent = index == position) }
        queueStateFlow.value = QueueState(items = items, currentItem = items[position], currentPosition = position, version = previous.version + 1, contentVersion = contentVersion + 1)
        return contentVersion + 1
    }
    override fun getQueue(): List<QueueItem> = queueStateFlow.value.items
    override fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> = queueStateFlow.value.items.also { shuffleModeQueueReads++ }
    override fun getCurrentItem(): QueueItem? = queueStateFlow.value.currentItem
    override fun getCurrentPosition(): Int? = queueStateFlow.value.currentPosition
    override fun getSize(): Int = queueStateFlow.value.items.size
    override fun setCurrentItem(currentItem: QueueItem) {}
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = nextItem
    override fun getPrevious(): QueueItem? = null
    override fun skipToNext(ignoreRepeat: Boolean): Boolean = false
    override fun skipToPrevious() {}
    override fun skipTo(position: Int) {}
    override suspend fun addToQueue(songs: List<Song>): Boolean = false
    override suspend fun addToNext(songs: List<Song>): Boolean = false
    override fun updateSongs(songs: List<Song>) {}
    override fun move(from: Int, to: Int) {}

    /** Every item passed to [remove], in order. */
    val removedItems = mutableListOf<QueueItem>()

    override fun remove(items: List<QueueItem>) {
        removedItems += items
    }
    override fun remove(song: Song) {}
    override fun clear() {}
    override fun getShuffleMode(): ShuffleMode = shuffleModeFlow.value
    override suspend fun setShuffleMode(shuffleMode: ShuffleMode, reshuffle: Boolean) {}
    override suspend fun toggleShuffleMode() {
        shuffleModeFlow.value = if (shuffleModeFlow.value == ShuffleMode.On) ShuffleMode.Off else ShuffleMode.On
    }
    override fun getRepeatMode(): RepeatMode = repeatModeFlow.value
    override fun setRepeatMode(repeatMode: RepeatMode) {}
    override fun toggleRepeatMode() {
        repeatModeFlow.value = when (repeatModeFlow.value) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
    }
}
