package com.simplecityapps.playback.queue

import com.simplecityapps.mediaprovider.model.Song
import timber.log.Timber

class QueueManager(private val queueWatcher: QueueWatcher) {

    enum class ShuffleMode {
        Off, On
    }

    enum class RepeatMode {
        Off, All, One
    }

    private var shuffleMode: ShuffleMode = ShuffleMode.Off

    private var repeatMode: RepeatMode = RepeatMode.Off

    private val baseQueue = Queue()

    private var currentItem: QueueItem? = null

    fun set(songs: List<Song>, position: Int = 0) {
        if (position < 0) {
            throw IllegalArgumentException("Queue position must be >= 0 (position $position)")
        }
        set(songs.mapIndexed { index, song -> song.toQueueItem(index == position) })
    }

    fun set(queueItems: List<QueueItem>) {
        baseQueue.set(queueItems)
        queueItems.firstOrNull { queueItem -> queueItem.isCurrent }?.let { currentItem ->
            setCurrentItem(currentItem)
        }
        queueWatcher.onQueueChanged()
    }

    fun setCurrentItem(currentItem: QueueItem) {
        Timber.v("setCurrentItem(): ${currentItem.song.path}, previous item: ${this.currentItem?.song?.path}")
        if (this.currentItem != currentItem) {
            this.currentItem = currentItem.clone(isCurrent = true)

            baseQueue.get(ShuffleMode.On).forEach { queueItem ->
                if (queueItem == currentItem) {
                    baseQueue.replace(queueItem, this.currentItem!!)
                } else if (queueItem.isCurrent) {
                    baseQueue.replace(queueItem, queueItem.clone(isCurrent = false))
                }
            }

            queueWatcher.onQueuePositionChanged()
        } else {
            Timber.v("setCurrentItem(): Item already current")
        }
    }

    fun getCurrentItem(): QueueItem? {
        return currentItem
    }

    fun getCurrentPosition(): Int? {
        val index = baseQueue.get(shuffleMode).indexOf(currentItem)
        if (index != -1) {
            return index
        }

        return null
    }

    fun getSize(): Int {
        return baseQueue.size()
    }

    fun remove(items: List<QueueItem>) {
        baseQueue.remove(items)
        queueWatcher.onQueueChanged()
    }

    /**
     * Retrieves the next queue item, accounting for the current repeat mode.=
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
        val currentQueue = baseQueue.get(shuffleMode)
        val currentIndex = currentQueue.indexOf(currentItem)

        return when (repeatMode) {
            RepeatMode.Off -> {
                currentQueue.getOrNull(currentIndex + 1)
            }
            RepeatMode.All -> {
                if (currentIndex == baseQueue.size() - 1) {
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
        val currentQueue = baseQueue.get(shuffleMode)
        return currentQueue.getOrNull(currentQueue.indexOf(currentItem) - 1)
    }

    fun getQueue(): List<QueueItem> {
        return baseQueue.get(shuffleMode)
    }

    fun setShuffleMode(shuffleMode: ShuffleMode) {
        if (this.shuffleMode != shuffleMode) {
            this.shuffleMode = shuffleMode
            if (shuffleMode == ShuffleMode.On) {
                baseQueue.shuffle()
            }
            queueWatcher.onShuffleChanged()
        }
    }

    fun getShuffleMode(): ShuffleMode {
        return shuffleMode
    }

    fun toggleShuffleMode() {
        when (shuffleMode) {
            ShuffleMode.Off -> setShuffleMode(ShuffleMode.On)
            ShuffleMode.On -> setShuffleMode(ShuffleMode.Off)
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

    fun skipToNext(ignoreRepeat: Boolean) {
        Timber.v("skipToNext()")
        getNext(ignoreRepeat)?.let { nextItem ->
            setCurrentItem(nextItem)
            queueWatcher.onQueuePositionChanged()
        } ?: Timber.v("No next track to skip to")
    }

    fun skipToPrevious() {
        Timber.v("skipToPrevious()")
        getPrevious()?.let { previousItem ->
            setCurrentItem(previousItem)
            queueWatcher.onQueuePositionChanged()
        } ?: Timber.v("No next track to skip-previous to")
    }

    fun skipTo(position: Int) {
        val currentQueue = baseQueue.get(shuffleMode)
        currentQueue.getOrNull(position)?.let { queueItem ->
            setCurrentItem(queueItem)
            queueWatcher.onQueuePositionChanged()
        } ?: run {
            Timber.e("Couldn't skip to position $position, no associated queue item found")
        }
    }


    /**
     * Holds a pair of lists, one representing the 'base' queue, and the other representing the 'shuffle' queue.
     */
    class Queue {

        private var baseList: MutableList<QueueItem> = mutableListOf()
        private var shuffleList: MutableList<QueueItem> = mutableListOf()

        fun get(shuffleMode: QueueManager.ShuffleMode): List<QueueItem> {
            return when (shuffleMode) {
                QueueManager.ShuffleMode.Off -> {
                    baseList
                }
                QueueManager.ShuffleMode.On -> {
                    shuffleList
                }
            }
        }

        fun set(items: List<QueueItem>) {
            baseList = items.toMutableList()

            shuffle()
        }

        fun shuffle() {
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

        fun remove(items: List<QueueItem>) {
            baseList.removeAll(items)
            shuffleList.removeAll(items)
        }

        fun replace(old: QueueItem, new: QueueItem) {
            baseList[baseList.indexOf(old)] = new
            shuffleList[shuffleList.indexOf(old)] = new
        }

        fun size(): Int {
            return baseList.size
        }
    }
}