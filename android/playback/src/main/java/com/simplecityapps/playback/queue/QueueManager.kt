package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

class QueueManager(
    private val queueWatcher: QueueWatcher,
    private val preferenceManager: GeneralPreferenceManager
) : QueueOperations {
    enum class ShuffleMode {
        Off,
        On
        ;

        companion object {
            fun init(ordinal: Int): ShuffleMode = when (ordinal) {
                On.ordinal -> On
                Off.ordinal -> Off
                else -> Off
            }
        }
    }

    enum class RepeatMode {
        Off,
        All,
        One
        ;

        companion object {
            fun init(ordinal: Int): RepeatMode = when (ordinal) {
                All.ordinal -> All
                One.ordinal -> One
                Off.ordinal -> Off
                else -> Off
            }
        }
    }

    private val _shuffleModeFlow = MutableStateFlow(ShuffleMode.Off)

    /**
     * The shuffle mode. Backs [getShuffleMode] directly, so the two can't disagree; it changes just
     * before [QueueChangeCallback.onShuffleChanged] is dispatched.
     */
    override val shuffleModeFlow: StateFlow<ShuffleMode> = _shuffleModeFlow.asStateFlow()

    // Renamed accessors: the defaults would clash with getShuffleMode()/setShuffleMode() on the JVM.
    private var shuffleMode: ShuffleMode
        @JvmName("currentShuffleMode")
        get() = _shuffleModeFlow.value

        @JvmName("updateShuffleMode")
        set(value) {
            _shuffleModeFlow.value = value
        }

    private val _repeatModeFlow = MutableStateFlow(RepeatMode.Off)

    /**
     * The repeat mode. Backs [getRepeatMode] directly, so the two can't disagree; it changes just
     * before [QueueChangeCallback.onRepeatChanged] is dispatched.
     */
    override val repeatModeFlow: StateFlow<RepeatMode> = _repeatModeFlow.asStateFlow()

    // Renamed accessors: the defaults would clash with getRepeatMode()/setRepeatMode() on the JVM.
    private var repeatMode: RepeatMode
        @JvmName("currentRepeatMode")
        get() = _repeatModeFlow.value

        @JvmName("updateRepeatMode")
        set(value) {
            _repeatModeFlow.value = value
        }

    private val queue = Queue()

    private var currentItem: QueueItem? = null

    private val _queueState = MutableStateFlow(QueueState.Empty)

    private var queueStateVersion = 0L

    private var queueContentVersion = 0L

    private var queueNonMoveContentVersion = 0L

    /**
     * The queue as the active shuffle mode presents it, with the current item and position.
     * Republished just before every [QueueChangeCallback.onQueueChanged] and
     * [QueueChangeCallback.onQueuePositionChanged] dispatch, and whenever the shuffle mode or
     * [clear] changes what [getQueue] or [getCurrentItem] return, and when [hasRestoredQueue] is set.
     */
    override val queueStateFlow: StateFlow<QueueState> = _queueState.asStateFlow()

    override var hasRestoredQueue = false
        set(value) {
            field = value
            publishQueueState()
            if (value) {
                queueWatcher.onQueueRestored()
            }
        }

    /**
     * Replaces the current queue.
     *
     * @param songs the songs to replace the current non-shuffle queue.
     * @param shuffleSongs the songs to replace the shuffle queue. If null, shuffle mode may be disabled. Defaults to null.
     * @param position the new queue position. Defaults to 0.
     *
     * @return true if the queue was successfully set, and is not empty.
     */
    override suspend fun setQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int
    ): Boolean {
        if (position < 0 || position >= songs.size) {
            Timber.e("Invalid queue position: $position (songs.size: ${songs.size})")
            return false
        }

        if (shuffleSongs == null && !preferenceManager.retainShuffleOnNewQueue) {
            setShuffleMode(ShuffleMode.Off, reshuffle = false)
        }

        var existingQueueChanged = false
        var shuffleQueueChanged = false

        var currentItem = currentItem

        withContext(Dispatchers.IO) {
            var baseQueue = getQueue(ShuffleMode.Off)
            if (baseQueue.size != songs.size || songs.map { it.id } != baseQueue.map { it.song.id }) {
                baseQueue = songs.map { song -> song.toQueueItem(false) }
                queue.setBaseQueue(baseQueue)
                existingQueueChanged = true
            }

            currentItem = baseQueue[position]

            if (shuffleSongs != null) {
                val existingShuffleQueue = getQueue(ShuffleMode.On)
                if (existingQueueChanged || (existingShuffleQueue.size != shuffleSongs.size || shuffleSongs.map { it.id } != existingShuffleQueue.map { it.song.id })) {
                    val queueOrderMap = shuffleSongs.withIndex().associate { songId -> songId.value.id to songId.index }
                    val shuffleQueue = baseQueue.sortedBy { queueItem -> queueOrderMap[queueItem.song.id] }
                    queue.setShuffleQueue(shuffleQueue)
                    if (shuffleMode == ShuffleMode.On) {
                        currentItem = shuffleQueue[position]
                    }
                    shuffleQueueChanged = true
                }
            } else {
                queue.generateShuffleQueue(currentItem)
                shuffleQueueChanged = true
            }
        }

        when (shuffleMode) {
            ShuffleMode.Off ->
                if (existingQueueChanged) {
                    notifyQueueChanged()
                }
            ShuffleMode.On ->
                if (shuffleQueueChanged) {
                    notifyQueueChanged()
                }
        }

        currentItem?.let {
            Timber.i("Current item is ${it.song.name}")
            setCurrentItem(it)
        }

        return queue.size() != 0
    }

    override fun setCurrentItem(currentItem: QueueItem) {
        Timber.v("setCurrentItem(currentItem: ${currentItem.song.name}|${currentItem.song.mimeType}), previous item: ${this.currentItem?.song?.name}|${this.currentItem?.song?.mimeType}")
        val oldPosition = getCurrentPosition()
        if (this.currentItem != currentItem) {
            this.currentItem = currentItem.clone(isCurrent = true)

            queue.get(shuffleMode).forEach { queueItem ->
                if (queueItem == currentItem) {
                    queue.replace(queueItem, this.currentItem!!)
                } else if (queueItem.isCurrent) {
                    queue.replace(queueItem, queueItem.clone(isCurrent = false))
                }
            }

            notifyQueuePositionChanged(oldPosition, getCurrentPosition())
        } else {
            Timber.v("setCurrentItem(): Item already current")
        }
    }

    override fun getCurrentItem(): QueueItem? = currentItem

    override fun getCurrentPosition(): Int? {
        val index = queue.get(shuffleMode).indexOf(currentItem)
        if (index != -1) {
            return index
        }

        return null
    }

    override fun getSize(): Int = queue.size()

    override fun remove(items: List<QueueItem>) {
        val oldPosition = getCurrentPosition()
        queue.remove(items)
        notifyQueueChanged()
        if (getCurrentPosition() != oldPosition) {
            notifyQueuePositionChanged(oldPosition, getCurrentPosition())
        }
    }

    override fun remove(song: Song) {
        val songQueueItem = getQueue().filter { it.song.id == song.id }
        remove(songQueueItem)
    }

    override fun clear() {
        Timber.v("clear()")
        queue.clear()
        notifyQueueChanged()
        currentItem = null
        // The callback above fires while the cleared item is still current (as it always has); the
        // flow follows up with the settled state.
        publishQueueState()
    }

    /**
     * Retrieves the next queue item, accounting for the current repeat mode.
     *
     * @param ignoreRepeat whether to ignore the current repeat mode, and return the next item as if repeat mode is 'all'
     */
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = if (ignoreRepeat) {
        getNext(RepeatMode.All)
    } else {
        getNext(repeatMode)
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

    override fun getPrevious(): QueueItem? {
        val currentQueue = queue.get(shuffleMode)
        return currentQueue.getOrNull(currentQueue.indexOf(currentItem) - 1)
    }

    override fun getQueue(): List<QueueItem> = queue.get(shuffleMode)

    override fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> = queue.get(shuffleMode)

    /**
     * Sets the shuffle mode
     *
     * @param shuffleMode [ShuffleMode]
     * @param reshuffle if true, re-shuffle the shuffle-queue when the [shuffleMode] is [ShuffleMode.On].
     */
    override suspend fun setShuffleMode(
        shuffleMode: ShuffleMode,
        reshuffle: Boolean
    ) {
        if (this.shuffleMode != shuffleMode) {
            val previousPosition = getCurrentPosition()

            this.shuffleMode = shuffleMode

            if (shuffleMode == ShuffleMode.On && reshuffle) {
                withContext(Dispatchers.IO) {
                    queue.generateShuffleQueue(currentItem)
                }
            }

            // getQueue() now presents the other list, even before the queue is restored, when no
            // onQueueChanged follows.
            publishQueueState()
            queueWatcher.onShuffleChanged(shuffleMode)

            if (hasRestoredQueue) {
                notifyQueueChanged() // The queue has been reshuffled, and shuffle is on, so the queue has changed

                if (previousPosition != getCurrentPosition()) {
                    notifyQueuePositionChanged(previousPosition, getCurrentPosition())
                }
            }
        }
    }

    override fun getShuffleMode(): ShuffleMode = shuffleMode

    override suspend fun toggleShuffleMode() {
        when (shuffleMode) {
            ShuffleMode.Off -> setShuffleMode(ShuffleMode.On, reshuffle = true)
            ShuffleMode.On -> setShuffleMode(ShuffleMode.Off, reshuffle = false)
        }
    }

    override fun setRepeatMode(repeatMode: RepeatMode) {
        if (this.repeatMode != repeatMode) {
            this.repeatMode = repeatMode
            queueWatcher.onRepeatChanged(repeatMode)
        }
    }

    override fun getRepeatMode(): RepeatMode = repeatMode

    override fun toggleRepeatMode() {
        when (repeatMode) {
            RepeatMode.Off -> setRepeatMode(RepeatMode.All)
            RepeatMode.All -> setRepeatMode(RepeatMode.One)
            RepeatMode.One -> setRepeatMode(RepeatMode.Off)
        }
    }

    override fun skipToNext(ignoreRepeat: Boolean): Boolean {
        Timber.v("skipToNext()")
        getNext(ignoreRepeat)?.let { nextItem ->
            setCurrentItem(nextItem)
            return true
        } ?: run {
            Timber.v("No next track to skip to")
            return false
        }
    }

    override fun skipToPrevious() {
        Timber.v("skipToPrevious()")
        getPrevious()?.let { previousItem ->
            setCurrentItem(previousItem)
        } ?: Timber.v("No next track to skip-previous to")
    }

    override fun skipTo(position: Int) {
        val currentQueue = queue.get(shuffleMode)
        currentQueue.getOrNull(position)?.let { queueItem ->
            setCurrentItem(queueItem)
        } ?: run {
            Timber.e("Couldn't skip to position $position, no associated queue item found")
        }
    }

    override fun addToQueue(songs: List<Song>) {
        queue.add(songs.map { song -> song.toQueueItem(false) })
        notifyQueueChanged()
    }

    override fun move(
        from: Int,
        to: Int
    ) {
        val oldPosition = getCurrentPosition()
        queue.move(from, to, shuffleMode)
        val currentPosition = getCurrentPosition()
        notifyQueueChanged(QueueChangeCallback.QueueChangeReason.Move)
        if (currentPosition != oldPosition) {
            notifyQueuePositionChanged(oldPosition, currentPosition)
        }
    }

    override fun addToNext(songs: List<Song>) {
        val items = songs.map { song -> song.toQueueItem(false) }
        val current = currentItem
        val baseIndex = current?.let { queue.get(ShuffleMode.Off).indexOf(it) } ?: -1
        val shuffleIndex = current?.let { queue.get(ShuffleMode.On).indexOf(it) } ?: -1
        queue.insert(baseIndex + 1, shuffleIndex + 1, items)
        notifyQueueChanged()
    }

    private fun notifyQueueChanged(reason: QueueChangeCallback.QueueChangeReason = QueueChangeCallback.QueueChangeReason.Unknown) {
        queueContentVersion++
        if (reason != QueueChangeCallback.QueueChangeReason.Move) {
            queueNonMoveContentVersion++
        }
        publishQueueState()
        queueWatcher.onQueueChanged(reason)
    }

    private fun notifyQueuePositionChanged(
        oldPosition: Int?,
        newPosition: Int?
    ) {
        publishQueueState()
        queueWatcher.onQueuePositionChanged(oldPosition, newPosition)
    }

    /**
     * The single update site for [queueStateFlow]. Copies the list, since [Queue] mutates its lists in place.
     * Bumps [QueueState.version] so every publish is a distinct value, even if the items, current item
     * and position are unchanged (e.g. a song's metadata was edited without moving it in the queue).
     */
    private fun publishQueueState() {
        queueStateVersion++
        _queueState.value = QueueState(
            items = getQueue().toList(),
            currentItem = currentItem,
            currentPosition = getCurrentPosition(),
            version = queueStateVersion,
            contentVersion = queueContentVersion,
            nonMoveContentVersion = queueNonMoveContentVersion,
            isRestored = hasRestoredQueue
        )
    }

    /**
     * Holds a pair of lists, one representing the 'base' queue, and the other representing the 'shuffle' queue.
     */
    class Queue {
        private var baseList: MutableList<QueueItem> = mutableListOf()
        private var shuffleList: MutableList<QueueItem> = mutableListOf()

        fun get(shuffleMode: ShuffleMode): MutableList<QueueItem> = when (shuffleMode) {
            ShuffleMode.Off -> baseList
            ShuffleMode.On -> shuffleList
        }

        fun getItem(
            shuffleMode: ShuffleMode,
            position: Int
        ): QueueItem? = get(shuffleMode).getOrNull(position)

        fun setBaseQueue(items: List<QueueItem>) {
            baseList = items.toMutableList()
        }

        fun setShuffleQueue(items: List<QueueItem>) {
            shuffleList = items.toMutableList()
        }

        fun generateShuffleQueue(selectedQueueItem: QueueItem?) {
            if (baseList.isEmpty()) {
                Timber.v("Cannot generate shuffle queue; base queue is empty")
                shuffleList = mutableListOf()
                return
            }

            shuffleList = baseList.shuffled().toMutableList()

            // Move the current item to the top of the shuffle list
            val currentIndex = shuffleList.indexOfFirst { it.uid == selectedQueueItem?.uid }
            if (currentIndex != -1) {
                shuffleList.add(0, shuffleList.removeAt(currentIndex))
            }
        }

        fun add(items: List<QueueItem>) {
            baseList.addAll(items)
            shuffleList.addAll(items)
        }

        fun insert(
            baseIndex: Int,
            shuffleIndex: Int,
            items: List<QueueItem>
        ) {
            baseList.addAll(baseIndex, items)
            shuffleList.addAll(shuffleIndex, items)
        }

        fun remove(items: List<QueueItem>) {
            baseList.removeAll(items)
            shuffleList.removeAll(items)
        }

        fun clear() {
            baseList.clear()
            shuffleList.clear()
        }

        fun replace(
            old: QueueItem,
            new: QueueItem
        ) {
            if (baseList.isNotEmpty()) {
                val baseIndex = baseList.indexOf(old)
                if (baseIndex != -1) {
                    baseList[baseIndex] = new
                }
            }
            if (shuffleList.isNotEmpty()) {
                val shuffleIndex = shuffleList.indexOf(old)
                if (shuffleIndex != -1) {
                    shuffleList[shuffleIndex] = new
                }
            }
        }

        fun move(
            from: Int,
            to: Int,
            shuffleMode: ShuffleMode
        ) {
            val list = get(shuffleMode)
            list.add(to, list.removeAt(from))
        }

        fun size(): Int = baseList.size
    }
}
