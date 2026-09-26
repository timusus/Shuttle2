package com.simplecityapps.playback.queue

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.chromecast.isRemote
import com.simplecityapps.playback.engine.PlayerThread
import com.simplecityapps.playback.engine.S2ShuffleOrder
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The queue, as a thin layer over [player]'s playlist. The playlist holds the whole queue in its unshuffled order,
 * each item tagged with its [QueueEntry]; its [S2ShuffleOrder] is the shuffled order, and its current item is the
 * queue's current item. Nothing is stored here that the player doesn't hold: every flow is derived from player
 * events.
 *
 * [player] is the local player, and holds the whole queue while casting too; a Cast receiver holds a window of it
 * (see [com.simplecityapps.playback.chromecast.CastQueue]).
 *
 * Callable from any thread, on [PlayerThread]'s rule: the player lives on the main thread, and calls that change the
 * queue run there (a suspend call switches to it, and any other call made off it is posted to it). Reads are safe from
 * any thread, and return the last published state.
 */
class QueueManager(
    private val player: ExoPlayer,
    private val playbackSettings: PlaybackSettings,
    private val songUriResolver: SongUriResolver,
    /** Where new queue entries are built: off the main thread, as a long queue takes a while. */
    private val buildContext: CoroutineContext = Dispatchers.Default,
    /** The player the app plays through: [player] itself, or a Cast player around it. */
    private val activePlayer: Player = player
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

    private val playerThread = PlayerThread(player)

    /**
     * Where a change to the queue goes. While playing locally, through [activePlayer], so what it reports is current as
     * soon as the change returns (a Cast player learns of changes made to [player] directly only later). While
     * casting, to [player], whose changes are sent on to the receiver. A shuffle order only [player] takes.
     */
    private val writer: Player
        get() = if (activePlayer.isRemote) player else activePlayer

    private val _shuffleModeFlow = MutableStateFlow(player.shuffleModeEnabled.toShuffleMode())

    /** The player's shuffle mode. It changes before the reshuffled queue is published on [queueStateFlow]. */
    override val shuffleModeFlow: StateFlow<ShuffleMode> = _shuffleModeFlow.asStateFlow()

    private val _repeatModeFlow = MutableStateFlow(player.repeatMode.toRepeatMode())

    /** The player's repeat mode. */
    override val repeatModeFlow: StateFlow<RepeatMode> = _repeatModeFlow.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState.Empty)

    /**
     * The queue as the active shuffle mode presents it, with the current item and position. Republished whenever
     * the player's playlist, shuffle order, shuffle mode or current item change, and when [hasRestoredQueue] is set.
     */
    override val queueStateFlow: StateFlow<QueueState> = _queueState.asStateFlow()

    /** Held by each change that builds new queue entries (see [withNewItems]). Fair, so they take turns in order. */
    private val newItemChanges = Mutex()

    /** The unshuffled and shuffled queue [queueStateFlow] last published. */
    @Volatile
    private var lists = Lists(emptyList(), emptyList())

    /** While a change made of several player calls is underway, publishing waits for it to finish. */
    private var batchDepth = 0

    override var hasRestoredQueue = false
        set(value) {
            field = value
            playerThread.run { publish() }
        }

    init {
        player.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int
                ) {
                    publish()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    publish()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeFlow.value = shuffleModeEnabled.toShuffleMode()
                    publish()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatModeFlow.value = repeatMode.toRepeatMode()
                }
            }
        )
    }

    override suspend fun setQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int
    ): Boolean = withNewItems({ NewQueue.build(songs, shuffleSongs, position) }) { queue -> applyQueue(queue) }

    override suspend fun buildQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int
    ): NewQueue = withContext(buildContext) { NewQueue.build(songs, shuffleSongs, position) }

    override fun setQueueIfContentVersion(
        contentVersion: Long,
        queue: NewQueue
    ): Long? {
        check(playerThread.isCurrent) { "setQueueIfContentVersion is main thread only" }
        if (_queueState.value.contentVersion != contentVersion) return null
        applyQueue(queue)
        return _queueState.value.contentVersion
    }

    /**
     * Builds what [change] takes off the main thread, then runs [change] with it on it. Changes made this way take
     * turns, so they apply in the order they're made, however long each takes to build: songs added while a new queue
     * is being built join it rather than the queue it replaces.
     */
    private suspend fun <B, T> withNewItems(
        build: () -> B,
        change: (B) -> T
    ): T = newItemChanges.withLock {
        val built = withContext(buildContext) { build() }
        withContext(Dispatchers.Main.immediate) { change(built) }
    }

    /** New entries for [songs], built as [withNewItems] builds them. */
    private fun newItems(songs: List<Song>): () -> List<MediaItem> = { songs.map { song -> song.toQueueEntry().toMediaItem() } }

    /**
     * Replaces the playlist with [queue]'s items, unless it already holds those songs, and moves to its position. When
     * it already holds them, it keeps its items and takes the queue's songs' data.
     */
    private fun applyQueue(queue: NewQueue): Boolean {
        val songs = queue.songs
        val shuffleSongs = queue.shuffleSongs
        val position = queue.position
        val savedShuffle = shuffleSongs?.takeIf { player.shuffleModeEnabled }
        val size = savedShuffle?.size ?: songs.size
        if (position < 0 || position >= size || songs.isEmpty()) {
            Timber.e("Invalid queue position: $position (size: $size, songs.size: ${songs.size})")
            return false
        }

        batch {
            if (shuffleSongs == null && !playbackSettings.retainShuffleOnNewQueue.value) {
                writer.shuffleModeEnabled = false
            }

            val sameSongs = player.mediaItemCount == songs.size &&
                songs.indices.all { index -> songs[index].id == player.getMediaItemAt(index).queueEntry.song.id }
            val index = if (savedShuffle != null) checkNotNull(queue.shuffledIndex) else position

            if (sameSongs) {
                replaceChanged(songs)
                if (index != player.currentMediaItemIndex) {
                    writer.seekTo(index, 0)
                }
            } else {
                songUriResolver.queued(queue.items)
                writer.setMediaItems(queue.items, index, 0)
            }
            player.setShuffleOrder(queue.shuffleOrder)
        }

        return player.mediaItemCount != 0
    }

    override fun getQueue(): List<QueueItem> = _queueState.value.items

    override fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> = lists.get(shuffleMode)

    override fun getCurrentItem(): QueueItem? = _queueState.value.currentItem

    override fun getCurrentPosition(): Int? = _queueState.value.currentPosition

    override fun getSize(): Int = lists.base.size

    override fun setCurrentItem(currentItem: QueueItem) {
        playerThread.run {
            val index = entries().indexOfFirst { it.uid == currentItem.uid }
            if (index != -1 && index != player.currentMediaItemIndex) {
                writer.seekTo(index, 0)
            }
        }
    }

    /**
     * The item after the current one, as the player would play it. [ignoreRepeat] treats the repeat mode as
     * [RepeatMode.All].
     */
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = nextIndex(if (ignoreRepeat) RepeatMode.All else repeatModeFlow.value)?.let { lists.base.getOrNull(it) }

    override fun getPrevious(): QueueItem? {
        val state = _queueState.value
        val position = state.currentPosition ?: return null
        return state.items.getOrNull(position - 1)
    }

    /** The playlist index of the item after the current one under [repeatMode], or null if there's none. */
    private fun nextIndex(repeatMode: RepeatMode): Int? {
        val lists = lists
        val state = _queueState.value
        val position = state.currentPosition ?: return null
        val presented = lists.get(state.shuffleMode)
        val next = when (repeatMode) {
            RepeatMode.Off -> presented.getOrNull(position + 1)
            RepeatMode.All -> presented.getOrNull(position + 1) ?: presented.firstOrNull()
            RepeatMode.One -> state.currentItem
        } ?: return null
        return lists.base.indexOf(next).takeIf { it != -1 }
    }

    override fun skipToNext(ignoreRepeat: Boolean): Boolean {
        Timber.v("skipToNext()")
        val next = nextIndex(if (ignoreRepeat) RepeatMode.All else repeatModeFlow.value)
        if (next == null) {
            Timber.v("No next track to skip to")
            return false
        }
        playerThread.run { writer.seekTo(next, 0) }
        return true
    }

    override fun skipToPrevious() {
        Timber.v("skipToPrevious()")
        getPrevious()?.let(::setCurrentItem) ?: Timber.v("No previous track to skip to")
    }

    override fun skipTo(position: Int) {
        getQueue().getOrNull(position)?.let(::setCurrentItem) ?: Timber.e("Couldn't skip to position $position, no associated queue item found")
    }

    /**
     * Adds [songs] to the end of the queue: the end of the unshuffled order, and the end of the shuffled order. Added
     * to an empty queue, they're set as a new queue, as [setQueue] sets it, and this returns true.
     */
    override suspend fun addToQueue(songs: List<Song>): Boolean = withNewItems(newItems(songs)) { items ->
        if (player.mediaItemCount == 0) return@withNewItems applyQueue(NewQueue.of(songs, items, null, 0))
        songUriResolver.queued(items)
        writer.addMediaItems(items)
        false
    }

    /**
     * Adds [songs] after the current item, in both the unshuffled and the shuffled order. Added to an empty queue,
     * they're set as a new queue, as [setQueue] sets it, and this returns true.
     */
    override suspend fun addToNext(songs: List<Song>): Boolean = withNewItems(newItems(songs)) { items ->
        if (player.mediaItemCount == 0) return@withNewItems applyQueue(NewQueue.of(songs, items, null, 0))
        batch {
            songUriResolver.queued(items)
            val current = player.currentMediaItemIndex
            val insertAt = current + 1
            val shuffled = shuffledIndices()
            writer.addMediaItems(insertAt, items)
            // The shuffled order places the new items right after the current one too.
            val shifted = shuffled.map { index -> if (index >= insertAt) index + items.size else index }
            val added = (insertAt until insertAt + items.size).toList()
            val currentPosition = shifted.indexOf(current)
            val order = shifted.subList(0, currentPosition + 1) + added + shifted.subList(currentPosition + 1, shifted.size)
            player.setShuffleOrder(S2ShuffleOrder(order.toIntArray()))
        }
        false
    }

    override fun updateSongs(songs: List<Song>) {
        val songsById = songs.associateBy { it.id }
        if (songsById.isEmpty()) return
        playerThread.run {
            batch {
                replaceChanged(entries().map { entry -> songsById[entry.song.id] ?: entry.song })
            }
        }
    }

    /**
     * Gives each playlist item the song at its index in [songs], where that differs, keeping its uid and its place in
     * both orders. Main thread only.
     */
    private fun replaceChanged(songs: List<Song>) {
        val shuffled = shuffledIndices()
        entries().zip(songs).forEachIndexed { index, (entry, song) ->
            if (song != entry.song) {
                val item = QueueEntry(entry.uid, song).toMediaItem()
                songUriResolver.queued(listOf(item))
                writer.replaceMediaItem(index, item)
            }
        }
        // An item whose file changed is replaced by removing and re-adding it, which moves it to the end of the
        // shuffled order, so the order is put back.
        if (shuffledIndices() != shuffled) {
            player.setShuffleOrder(S2ShuffleOrder(shuffled.toIntArray()))
        }
    }

    /** Moves an item within the queue as the shuffle mode presents it, leaving the other order as it is. */
    override fun move(
        from: Int,
        to: Int
    ) {
        playerThread.run {
            if (player.shuffleModeEnabled) {
                val order = shuffledIndices().toMutableList()
                if (from !in order.indices || to !in order.indices) return@run
                order.add(to, order.removeAt(from))
                player.setShuffleOrder(S2ShuffleOrder(order.toIntArray()))
            } else {
                writer.moveMediaItem(from, to)
            }
        }
    }

    override fun remove(items: List<QueueItem>) {
        val uids = items.map { it.uid }.toSet()
        playerThread.run {
            batch {
                // Each player change rebuilds the timeline, so a run of items goes in one call, the last run first.
                val indices = entries().withIndex().filter { it.value.uid in uids }.map { it.index }
                val runs = mutableListOf<IntRange>()
                indices.forEach { index ->
                    val last = runs.lastOrNull()
                    if (last != null && last.last + 1 == index) runs[runs.lastIndex] = last.first..index else runs += index..index
                }
                runs.asReversed().forEach { range -> writer.removeMediaItems(range.first, range.last + 1) }
            }
        }
    }

    override fun remove(song: Song) {
        remove(getQueue().filter { it.song.id == song.id })
    }

    override fun clear() {
        Timber.v("clear()")
        playerThread.run { writer.clearMediaItems() }
    }

    override fun getShuffleMode(): ShuffleMode = shuffleModeFlow.value

    /**
     * Sets the shuffle mode. [reshuffle] generates a new shuffled order, starting at the current item, when turning
     * shuffle on.
     */
    override suspend fun setShuffleMode(
        shuffleMode: ShuffleMode,
        reshuffle: Boolean
    ) = withContext(Dispatchers.Main.immediate) {
        if (player.shuffleModeEnabled.toShuffleMode() == shuffleMode) return@withContext
        batch {
            if (shuffleMode == ShuffleMode.On && reshuffle) {
                val current = player.currentMediaItemIndex.takeIf { player.mediaItemCount > 0 } ?: C.INDEX_UNSET
                player.setShuffleOrder(S2ShuffleOrder.shuffled(player.mediaItemCount, firstIndex = current))
            }
            writer.shuffleModeEnabled = shuffleMode == ShuffleMode.On
        }
    }

    override suspend fun toggleShuffleMode() = when (getShuffleMode()) {
        ShuffleMode.Off -> setShuffleMode(ShuffleMode.On, reshuffle = true)
        ShuffleMode.On -> setShuffleMode(ShuffleMode.Off, reshuffle = false)
    }

    override fun getRepeatMode(): RepeatMode = repeatModeFlow.value

    override fun setRepeatMode(repeatMode: RepeatMode) {
        playerThread.run { writer.repeatMode = repeatMode.toPlayerRepeatMode() }
    }

    override fun toggleRepeatMode() {
        when (getRepeatMode()) {
            RepeatMode.Off -> setRepeatMode(RepeatMode.All)
            RepeatMode.All -> setRepeatMode(RepeatMode.One)
            RepeatMode.One -> setRepeatMode(RepeatMode.Off)
        }
    }

    /** The playlist's entries, in unshuffled order. Main thread only. */
    private fun entries(): List<QueueEntry> = List(player.mediaItemCount) { index -> player.getMediaItemAt(index).queueEntry }

    /** The playlist indices in shuffled order. Main thread only. */
    private fun shuffledIndices(): List<Int> {
        val timeline = player.currentTimeline
        return generateSequence(timeline.getFirstWindowIndex(true).takeIf { it != C.INDEX_UNSET }) { index ->
            timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true).takeIf { it != C.INDEX_UNSET }
        }.toList()
    }

    /** Runs [block], then publishes once, rather than once per player call it makes. Main thread only. */
    private fun batch(block: () -> Unit) {
        batchDepth++
        try {
            block()
        } finally {
            batchDepth--
        }
        publish()
    }

    /**
     * Publishes the player's queue, if it differs from the last published. The versions record what changed
     * since: the order or membership of the presented items ([QueueState.contentVersion]), anything but a
     * reordering with the same items and shuffle mode ([QueueState.nonMoveContentVersion]), or only their song
     * data ([QueueState.songDataVersion]).
     */
    private fun publish() {
        if (batchDepth > 0) return
        val entries = entries()
        songUriResolver.retainOnly(entries)
        val current = player.currentMediaItemIndex.takeIf { entries.isNotEmpty() }
        val base = entries.mapIndexed { index, entry -> entry.toQueueItem(isCurrent = index == current) }
        val shuffled = shuffledIndices().map { base[it] }
        val shuffleMode = player.shuffleModeEnabled.toShuffleMode()
        val items = if (shuffleMode == ShuffleMode.On) shuffled else base
        val currentItem = current?.let { base[it] }

        val previous = _queueState.value
        val uids = items.map { it.uid }
        val previousUids = previous.items.map { it.uid }
        val songsChanged = items.map { it.song } != previous.items.map { it.song }
        val currentPosition = currentItem?.let { items.indexOf(it) }?.takeIf { it != -1 }
        val unchanged = uids == previousUids && !songsChanged && currentItem?.uid == previous.currentItem?.uid &&
            currentPosition == previous.currentPosition && shuffleMode == previous.shuffleMode && hasRestoredQueue == previous.isRestored
        if (unchanged) return

        val contentChanged = uids != previousUids
        val moveOnly = contentChanged && shuffleMode == previous.shuffleMode && uids.sorted() == previousUids.sorted()
        lists = Lists(base, shuffled)
        _queueState.value = QueueState(
            items = items,
            currentItem = currentItem,
            currentPosition = currentPosition,
            version = previous.version + 1,
            contentVersion = previous.contentVersion + if (contentChanged) 1 else 0,
            nonMoveContentVersion = previous.nonMoveContentVersion + if (contentChanged && !moveOnly) 1 else 0,
            songDataVersion = previous.songDataVersion + if (!contentChanged && songsChanged) 1 else 0,
            isRestored = hasRestoredQueue,
            shuffleMode = shuffleMode
        )
    }

    private class Lists(
        val base: List<QueueItem>,
        val shuffled: List<QueueItem>
    ) {
        fun get(shuffleMode: ShuffleMode): List<QueueItem> = when (shuffleMode) {
            ShuffleMode.Off -> base
            ShuffleMode.On -> shuffled
        }
    }
}

private fun Boolean.toShuffleMode(): QueueManager.ShuffleMode = if (this) QueueManager.ShuffleMode.On else QueueManager.ShuffleMode.Off

fun Int.toRepeatMode(): QueueManager.RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> QueueManager.RepeatMode.All
    Player.REPEAT_MODE_ONE -> QueueManager.RepeatMode.One
    else -> QueueManager.RepeatMode.Off
}

fun QueueManager.RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    QueueManager.RepeatMode.Off -> Player.REPEAT_MODE_OFF
    QueueManager.RepeatMode.All -> Player.REPEAT_MODE_ALL
    QueueManager.RepeatMode.One -> Player.REPEAT_MODE_ONE
}
