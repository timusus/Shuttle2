package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The queue without a player: its items in queue order, their [ShuffleOrder], the current item and the shuffle and
 * repeat modes. On Android the Media3 playlist is the queue (`QueueFacade`, `PlaylistEditor`, `QueueStatePublisher` in
 * android/playback); iOS has no player that holds a playlist, so this holds it instead, with the same rules: each
 * method here is the Media3 playlist operation `PlaylistEditor` makes, through the same [ShuffleOrder] and
 * [NewQueueOrder], and publishing is [republished], as `QueueStatePublisher.publish` does it.
 *
 * Plain state, no threading of its own: its owner (the iOS player controller) calls it on the main thread. The flows
 * can be read from any thread.
 */
class QueueModel(
    private val random: Random = Random.Default
) {
    private class Entry(
        val uid: Long,
        val song: Song
    )

    /** The queue in queue order. */
    private var entries: List<Entry> = emptyList()

    /** The shuffled order, as indices into [entries]. */
    private var shuffleOrder: ShuffleOrder = ShuffleOrder.Empty

    /** The current item's index in [entries], or null when the queue is empty. */
    private var currentIndex: Int? = null

    private val _shuffleModeFlow = MutableStateFlow(ShuffleMode.Off)

    /** The shuffle mode. It changes before the reshuffled queue is published on [queueStateFlow]. */
    val shuffleModeFlow: StateFlow<ShuffleMode> = _shuffleModeFlow.asStateFlow()

    private val _repeatModeFlow = MutableStateFlow(RepeatMode.Off)

    val repeatModeFlow: StateFlow<RepeatMode> = _repeatModeFlow.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState.Empty)

    /** The queue as the shuffle mode presents it, with the current item and position; republished on every change. */
    val queueStateFlow: StateFlow<QueueState> = _queueState.asStateFlow()

    /** The queue in both orders, as last published. */
    var lists = QueueLists.Empty
        private set

    /** Whether the saved queue has been restored; published as [QueueState.isRestored]. */
    var isRestored: Boolean = false
        set(value) {
            field = value
            publish()
        }

    /**
     * What the queue was started from; published as [QueueState.playContext] with the next change to the queue, so
     * it's set just before the queue it goes with. Read from any thread.
     */
    @Volatile
    var playContext: PlayContext = PlayContext.None

    val shuffleMode: ShuffleMode
        get() = _shuffleModeFlow.value

    val repeatMode: RepeatMode
        get() = _repeatModeFlow.value

    val size: Int
        get() = entries.size

    val currentItem: QueueItem?
        get() = _queueState.value.currentItem

    /** Items shuffle plays them in when [shuffleMode] is on, else queue order: the order the queue is presented and played in. */
    private fun playOrder(): List<Int> = if (shuffleMode == ShuffleMode.On) shuffleOrder.toList() else entries.indices.toList()

    /**
     * Replaces the queue with [songs], the item at [position] current, as `PlaylistEditor.setQueue` does. [shuffleSongs]
     * is a saved shuffled order of the same songs, and [position] is in it when shuffle is on; without it, a new shuffled
     * order starts at [position]. Sets the shuffle mode to [shuffleMode] if given; else, without [shuffleSongs], turns
     * shuffle off unless [retainShuffle]. When the queue already holds [songs], it keeps its items (their uids) and takes
     * the songs' data.
     *
     * @return false, leaving the queue alone, if [songs] is empty or [position] is out of range.
     */
    fun setQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int,
        shuffleMode: ShuffleMode? = null,
        retainShuffle: Boolean = false
    ): Boolean {
        val order = NewQueueOrder.of(songs.map { it.id }, shuffleSongs?.map { it.id }, position, random)
        val index = order.startIndex(shuffleEnabled = (shuffleMode ?: this.shuffleMode) == ShuffleMode.On) ?: return false
        _shuffleModeFlow.value = NewQueueOrder.shuffleModeAfter(shuffleMode, this.shuffleMode, hasSavedShuffle = shuffleSongs != null, retainShuffle = retainShuffle)

        val sameSongs = entries.size == songs.size && songs.indices.all { i -> songs[i].id == entries[i].song.id }
        entries = if (sameSongs) entries.zip(songs) { entry, song -> if (song == entry.song) entry else Entry(entry.uid, song) } else songs.map(::newEntry)
        shuffleOrder = order.shuffleOrder
        currentIndex = index
        publish()
        return true
    }

    /** Makes the item with [uid] current, if the queue holds it. */
    fun setCurrent(uid: Long) {
        val index = entries.indexOfFirst { it.uid == uid }
        if (index != -1) {
            currentIndex = index
            publish()
        }
    }

    /** The item after the current one under [repeatMode], as the queue plays. */
    fun next(repeatMode: RepeatMode = this.repeatMode): QueueItem? = _queueState.value.next(repeatMode)

    /** The item before the current one in the presented order; none before the first. */
    fun previous(): QueueItem? = _queueState.value.previous()

    /** The item after the one with [uid] in the presented order, not wrapping: where a failed item skips to. */
    fun following(uid: Long): QueueItem? {
        val items = _queueState.value.items
        val position = items.indexOfFirst { it.uid == uid }.takeIf { it != -1 } ?: return null
        return items.getOrNull(position + 1)
    }

    /** Adds [songs] to the end of both orders. Added to an empty queue, they're set as a new queue, and this returns true. */
    fun add(
        songs: List<Song>,
        retainShuffle: Boolean = false
    ): Boolean {
        if (songs.isEmpty()) return false
        if (entries.isEmpty()) return setQueue(songs, null, 0, retainShuffle = retainShuffle)
        shuffleOrder = shuffleOrder.inserted(entries.size, songs.size)
        entries = entries + songs.map(::newEntry)
        publish()
        return false
    }

    /** Adds [songs] after the current item in both orders. Added to an empty queue, they're set as a new queue, and this returns true. */
    fun addNext(
        songs: List<Song>,
        retainShuffle: Boolean = false
    ): Boolean {
        if (songs.isEmpty()) return false
        val current = currentIndex
        if (entries.isEmpty() || current == null) return setQueue(songs, null, 0, retainShuffle = retainShuffle)
        val insertAt = current + 1
        shuffleOrder = shuffleOrder.insertedNext(current, songs.size)
        entries = entries.subList(0, insertAt) + songs.map(::newEntry) + entries.subList(insertAt, entries.size)
        publish()
        return false
    }

    /** Gives each item whose song id is in [songsById] that song's data, keeping its uid and place. */
    fun updateSongs(songsById: Map<Long, Song>) {
        entries = entries.map { entry -> songsById[entry.song.id]?.takeIf { it != entry.song }?.let { Entry(entry.uid, it) } ?: entry }
        publish()
    }

    /**
     * Moves an item within the queue as the shuffle mode presents it, leaving the other order as it is. Queue order
     * follows Media3's `moveMediaItem`: [to] past the end moves to the end, and the shuffled order keeps every item's
     * place.
     */
    fun move(
        from: Int,
        to: Int
    ) {
        if (shuffleMode == ShuffleMode.On) {
            shuffleOrder = shuffleOrder.movedInOrder(from, to) ?: return
        } else {
            if (from !in entries.indices || to < 0) return
            val target = minOf(to, entries.lastIndex)
            if (from == target) return
            val list = entries.toMutableList()
            list.add(target, list.removeAt(from))
            val current = currentIndex?.let { entries[it].uid }
            shuffleOrder = shuffleOrder.moved(from, from + 1, target)
            entries = list
            currentIndex = current?.let { uid -> entries.indexOfFirst { it.uid == uid } }
        }
        publish()
    }

    /**
     * Removes the items with [uids]. When the current item goes, the next one that stays becomes current, as Media3
     * picks it: the one after it as the queue plays under the repeat mode, else (none after it, or repeating one) the
     * first in the presented order.
     *
     * @return true if the current item went with every item after it in queue order: Media3 ends playback then.
     */
    fun remove(uids: Set<Long>): Boolean {
        val removed = entries.indices.filter { entries[it].uid in uids }
        if (removed.isEmpty()) return false
        val removedSet = removed.toSet()
        val current = currentIndex
        val newIndexOf = IntArray(entries.size) { -1 }
        entries.indices.filterNot { it in removedSet }.forEachIndexed { newIndex, oldIndex -> newIndexOf[oldIndex] = newIndex }

        var playedOut = false
        val newCurrentOld: Int? = when {
            current == null -> null

            current !in removedSet -> current

            else -> {
                playedOut = (current until entries.size).all { it in removedSet }
                subsequent(current, removedSet)
            }
        }

        val newOrder = ShuffleOrder(shuffleOrder.toList().filterNot { it in removedSet }.map { newIndexOf[it] })
        entries = entries.filterIndexed { index, _ -> index !in removedSet }
        shuffleOrder = newOrder
        currentIndex = when {
            entries.isEmpty() -> null
            newCurrentOld != null -> newIndexOf[newCurrentOld]
            else -> if (shuffleMode == ShuffleMode.On) newOrder.firstIndex else 0
        }
        publish()
        return playedOut
    }

    /** The first item after [index] in the play order under the repeat mode that isn't in [removed]; null if none is. */
    private fun subsequent(
        index: Int,
        removed: Set<Int>
    ): Int? {
        if (repeatMode == RepeatMode.One) return null
        val order = playOrder()
        val start = order.indexOf(index)
        val after = order.subList(start + 1, order.size) + if (repeatMode == RepeatMode.All) order.subList(0, start) else emptyList()
        return after.firstOrNull { it !in removed }
    }

    fun clear() {
        entries = emptyList()
        shuffleOrder = ShuffleOrder.Empty
        currentIndex = null
        publish()
    }

    /** Sets the shuffle mode. [reshuffle] makes a new shuffled order, starting at the current item, when turning shuffle on. */
    fun setShuffleMode(
        shuffleMode: ShuffleMode,
        reshuffle: Boolean
    ) {
        if (this.shuffleMode == shuffleMode) return
        if (shuffleMode == ShuffleMode.On && reshuffle) {
            shuffleOrder = ShuffleOrder.shuffled(entries.size, firstIndex = currentIndex, random = random)
        }
        _shuffleModeFlow.value = shuffleMode
        publish()
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        _repeatModeFlow.value = repeatMode
    }

    private fun newEntry(song: Song) = Entry(song.toQueueItem(isCurrent = false).uid, song)

    /** Publishes the queue, if it differs from the last published, as `QueueStatePublisher` does (see [republished]). */
    private fun publish() {
        val published = _queueState.value.republished(
            size = entries.size,
            shuffledIndices = shuffleOrder.toList(),
            currentIndex = currentIndex,
            shuffleMode = shuffleMode,
            isRestored = isRestored,
            playContext = playContext
        ) { index, isCurrent -> QueueItem(entries[index].uid, entries[index].song, isCurrent) } ?: return
        lists = published.lists
        _queueState.value = published.state
    }
}
