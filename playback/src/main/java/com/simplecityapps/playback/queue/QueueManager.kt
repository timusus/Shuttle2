package com.simplecityapps.playback.queue

import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class QueueManager(private val queueWatcher: QueueWatcher) {

    enum class ShuffleMode {
        Off, On;

        companion object {
            fun init(ordinal: Int): ShuffleMode {
                return when (ordinal) {
                    On.ordinal -> On
                    Off.ordinal -> Off
                    else -> Off
                }
            }
        }
    }

    enum class RepeatMode {
        Off, All, One;

        companion object {
            fun init(ordinal: Int): RepeatMode {
                return when (ordinal) {
                    All.ordinal -> All
                    One.ordinal -> One
                    Off.ordinal -> Off
                    else -> Off
                }
            }
        }
    }

    private var shuffleMode: ShuffleMode = ShuffleMode.Off

    private var repeatMode: RepeatMode = RepeatMode.Off

    private val queue = Queue()

    private var currentItem: QueueItem? = null

    suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>? = null, position: Int = 0) {
        if (position < 0) {
            throw IllegalArgumentException("Queue position must be >= 0 (position $position)")
        }

        withContext(Dispatchers.IO) {
            val queueItems = songs.map { song -> song.toQueueItem(false) }
            queue.setQueue(queueItems)
            shuffleSongs?.mapNotNull { song ->
                queueItems.firstOrNull { queueItem -> queueItem.song == song }
            }?.let { shuffleQueueItems ->
                queue.setShuffleQueue(shuffleQueueItems)
            } ?: queue.generateShuffleQueue()
        }

        queue.getItem(shuffleMode, position)?.let { currentItem ->
            setCurrentItem(currentItem)
        }
        queueWatcher.onQueueChanged()
    }

    fun setCurrentItem(currentItem: QueueItem) {
        Timber.v("setCurrentItem(): ${currentItem.song.path}, previous item: ${this.currentItem?.song?.path}")
        val oldPosition = getCurrentPosition()
        if (this.currentItem != currentItem) {
            this.currentItem = currentItem.clone(isCurrent = true)

            queue.get(ShuffleMode.On).forEach { queueItem ->
                if (queueItem == currentItem) {
                    queue.replace(queueItem, this.currentItem!!)
                } else if (queueItem.isCurrent) {
                    queue.replace(queueItem, queueItem.clone(isCurrent = false))
                }
            }

            queueWatcher.onQueuePositionChanged(oldPosition, getCurrentPosition())
        } else {
            Timber.v("setCurrentItem(): Item already current")
        }
    }

    fun getCurrentItem(): QueueItem? {
        return currentItem
    }

    fun getCurrentPosition(): Int? {
        val index = queue.get(shuffleMode).indexOf(currentItem)
        if (index != -1) {
            return index
        }

        return null
    }

    fun getSize(): Int {
        return queue.size()
    }

    fun remove(items: List<QueueItem>) {
        queue.remove(items)
        queueWatcher.onQueueChanged()
    }

    /**
     * Retrieves the next queue item, accounting for the current repeat mode.
     *
     * @param ignoreRepeat whether to ignore the current repeat mode, and return the next item as if repeat mode is 'all'
     */
    fun getNext(ignoreRepeat: Boolean = false): QueueItem? {
        return if (ignoreRepeat) {
            getNext(RepeatMode.All)
        } else {
            getNext(repeatMode)
        }
    }

    private fun getNext(repeatMode: RepeatMode): QueueItem? {
        val currentQueue = queue.get(shuffleMode)
        val currentIndex = currentQueue.indexOf(currentItem)

        return when (repeatMode) {
            RepeatMode.Off -> {
                currentQueue.getOrNull(currentIndex + 1)
            }
            RepeatMode.All -> {
                if (currentIndex == queue.size() - 1) {
                    currentQueue.getOrNull(0)
                } else {
                    currentQueue.getOrNull(currentIndex + 1)
                }
            }
            RepeatMode.One -> {
                currentItem
            }
        }
    }

    fun getPrevious(): QueueItem? {
        val currentQueue = queue.get(shuffleMode)
        return currentQueue.getOrNull(currentQueue.indexOf(currentItem) - 1)
    }

    fun getQueue(): List<QueueItem> {
        return queue.get(shuffleMode)
    }

    fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> {
        return queue.get(shuffleMode)
    }

    suspend fun setShuffleMode(shuffleMode: ShuffleMode, reshuffle: Boolean) {
        if (this.shuffleMode != shuffleMode) {
            withContext(Dispatchers.IO) {
                this@QueueManager.shuffleMode = shuffleMode
                if (shuffleMode == ShuffleMode.On && reshuffle) {
                    queue.generateShuffleQueue()
                }
            }

            queueWatcher.onQueueChanged()
            queueWatcher.onShuffleChanged()
        }
    }

    fun getShuffleMode(): ShuffleMode {
        return shuffleMode
    }

    suspend fun toggleShuffleMode() {
        when (shuffleMode) {
            ShuffleMode.Off -> setShuffleMode(ShuffleMode.On, reshuffle = true)
            ShuffleMode.On -> setShuffleMode(ShuffleMode.Off, reshuffle = false)
        }
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        if (this.repeatMode != repeatMode) {
            this.repeatMode = repeatMode
            queueWatcher.onRepeatChanged()
        }
    }

    fun getRepeatMode(): RepeatMode {
        return repeatMode
    }

    fun toggleRepeatMode() {
        when (repeatMode) {
            RepeatMode.Off -> setRepeatMode(RepeatMode.All)
            RepeatMode.All -> setRepeatMode(RepeatMode.One)
            RepeatMode.One -> setRepeatMode(RepeatMode.Off)
        }
    }

    fun skipToNext(ignoreRepeat: Boolean = false): Boolean {
        Timber.v("skipToNext()")
        getNext(ignoreRepeat)?.let { nextItem ->
            setCurrentItem(nextItem)
            return true
        } ?: run {
            Timber.v("No next track to skip to")
            return false
        }
    }

    fun skipToPrevious() {
        Timber.v("skipToPrevious()")
        getPrevious()?.let { previousItem ->
            setCurrentItem(previousItem)
        } ?: Timber.v("No next track to skip-previous to")
    }

    fun skipTo(position: Int) {
        val currentQueue = queue.get(shuffleMode)
        currentQueue.getOrNull(position)?.let { queueItem ->
            setCurrentItem(queueItem)
        } ?: run {
            Timber.e("Couldn't skip to position $position, no associated queue item found")
        }
    }

    fun addToQueue(songs: List<Song>) {
        queue.add(songs.map { song -> song.toQueueItem(false) })
        queueWatcher.onQueueChanged()
    }

    fun move(from: Int, to: Int) {
        val oldPosition = getCurrentPosition()
        queue.move(from, to, shuffleMode)
        val currentPosition = getCurrentPosition()
        queueWatcher.onQueueChanged()
        if (currentPosition != oldPosition) {
            queueWatcher.onQueuePositionChanged(oldPosition, currentPosition)
        }
    }

    fun playNext(songs: List<Song>) {
        queue.insert((getCurrentPosition() ?: -1) + 1, songs.map { song -> song.toQueueItem(false) })
        queueWatcher.onQueueChanged()
    }


    /**
     * Holds a pair of lists, one representing the 'base' queue, and the other representing the 'shuffle' queue.
     */
    class Queue {

        private var baseList: MutableList<QueueItem> = mutableListOf()
        private var shuffleList: MutableList<QueueItem> = mutableListOf()

        fun get(shuffleMode: ShuffleMode): MutableList<QueueItem> {
            return when (shuffleMode) {
                ShuffleMode.Off -> baseList
                ShuffleMode.On -> shuffleList
            }
        }

        fun getItem(shuffleMode: ShuffleMode, position: Int): QueueItem? {
            return when (shuffleMode) {
                ShuffleMode.Off -> baseList.getOrNull(position)
                ShuffleMode.On -> shuffleList.getOrNull(position)
            }
        }

        fun setQueue(items: List<QueueItem>) {
            baseList = items.toMutableList()
        }

        fun setShuffleQueue(items: List<QueueItem>) {
            shuffleList = items.toMutableList()
        }

        fun generateShuffleQueue() {
            if (baseList.isEmpty()) {
                Timber.i("Cannot generate shuffle queue; base queue is empty")
                shuffleList = mutableListOf()
                return
            }

            shuffleList = baseList.shuffled().toMutableList()

            // Move the current item to the top of the shuffle list
            val currentIndex = shuffleList.indexOfFirst { it.isCurrent }
            if (currentIndex != -1) {
                shuffleList.add(0, shuffleList.removeAt(currentIndex))
            }
        }

        fun add(items: List<QueueItem>) {
            baseList.addAll(items)
            shuffleList.addAll(items.shuffled())
        }

        fun insert(position: Int, items: List<QueueItem>) {
            baseList.addAll(position, items)
            shuffleList.addAll(position, items)
        }

        fun remove(items: List<QueueItem>) {
            baseList.removeAll(items)
            shuffleList.removeAll(items)
        }

        fun replace(old: QueueItem, new: QueueItem) {
            baseList[baseList.indexOf(old)] = new
            shuffleList[shuffleList.indexOf(old)] = new
        }

        fun move(from: Int, to: Int, shuffleMode: ShuffleMode) {
            val list = get(shuffleMode)
            list.add(to, list.removeAt(from))
        }

        fun size(): Int {
            return baseList.size
        }
    }
}