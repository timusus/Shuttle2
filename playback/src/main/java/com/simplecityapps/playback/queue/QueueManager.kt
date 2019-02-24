package com.simplecityapps.playback.queue

import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Single

interface QueueChangeCallback {

    fun onQueueChanged()

    fun onShuffleChanged()

    fun onRepeatChanged()
}

class QueueResult(val queue: List<QueueItem>, val shuffleQueue: List<QueueItem>, val history: List<QueueItem>)

class QueueManager : QueueChangeCallback {

    enum class ShuffleMode {
        Off, On
    }

    enum class RepeatMode {
        Off, All, One
    }

    private var shuffleMode: ShuffleMode = ShuffleMode.Off

    private var repeatMode: RepeatMode = RepeatMode.Off

    private val baseQueue = Queue()

    private val history = mutableListOf<QueueItem>()

    private var currentItem: QueueItem? = null

    private var callbacks: MutableList<QueueChangeCallback> = mutableListOf()

    fun set(songs: List<Song>, position: Int = 0) {
        if (position < 0) {
            throw IllegalArgumentException("Queue position must be >= 0 (position $position)")
        }
        val queueItems = songs.mapIndexed { index, song -> song.toQueueItem(index == position) }
        baseQueue.set(queueItems)

        onQueueChanged()
    }

    fun setCurrentItem(currentItem: QueueItem) {
        this.currentItem = currentItem.clone(isCurrent = true)

        baseQueue.get(ShuffleMode.On).forEach { queueItem ->
            if (queueItem == currentItem) {
                baseQueue.replace(queueItem, this.currentItem!!)
            } else if (queueItem.isCurrent) {
                baseQueue.replace(queueItem, queueItem.clone(isCurrent = false))
            }
        }

        onQueueChanged()
    }

    fun getCurrentItem(): QueueItem? {
        return currentItem
    }

    fun remove(items: List<QueueItem>) {
        baseQueue.remove(items)
        onQueueChanged()
    }

    fun getNext(): QueueItem? {
        val currentQueue = baseQueue.get(shuffleMode)
        return currentQueue.getOrNull(currentQueue.indexOf(currentItem) + 1)
    }

    fun getPrevious(): QueueItem? {
        return null
    }

    fun getQueue(): Single<QueueResult> {
        return Single.just(
            QueueResult(
                baseQueue.get(ShuffleMode.Off),
                baseQueue.get(ShuffleMode.On),
                history
            )
        )
    }

    fun setShuffleMode(shuffleMode: ShuffleMode) {
        if (this.shuffleMode != shuffleMode) {
            this.shuffleMode = shuffleMode
            onRepeatChanged()
        }
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        if (this.repeatMode != repeatMode) {
            this.repeatMode = repeatMode

        }
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        callbacks.forEach { callback -> callback.onQueueChanged() }
    }

    override fun onShuffleChanged() {
        callbacks.forEach { callback -> callback.onShuffleChanged() }
    }

    override fun onRepeatChanged() {
        callbacks.forEach { callback -> callback.onRepeatChanged() }
    }

    fun addCallback(callback: QueueChangeCallback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: QueueChangeCallback) {
        callbacks.remove(callback)
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
            shuffleList = baseList.shuffled().toMutableList()
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