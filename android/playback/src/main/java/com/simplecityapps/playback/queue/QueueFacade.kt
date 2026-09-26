package com.simplecityapps.playback.queue

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.engine.PlayerThread
import com.simplecityapps.playback.engine.S2ShuffleOrder
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * [QueueOperations] over [player]'s playlist, which is the queue. The playlist holds the whole queue in its unshuffled
 * order, each item tagged with its [QueueEntry]; its [S2ShuffleOrder] is the shuffled order, and its current item is
 * the queue's current item. Nothing is stored here that the player doesn't hold. Calls are forwarded to the pieces
 * that do the work: [QueueStatePublisher] derives the flows from player events, [PlaylistEditor] makes each change on
 * the player, and [QueueBuilder] builds new items off the main thread and applies them in the order they were made.
 *
 * [player] is the local player, and holds the whole queue while casting too; a Cast receiver holds a window of it
 * (see [com.simplecityapps.playback.chromecast.CastQueue]).
 *
 * Callable from any thread, on [PlayerThread]'s rule: the player lives on the main thread, and calls that change the
 * queue run there (a suspend call switches to it, and any other call made off it is posted to it). Reads are safe from
 * any thread, and return the last published state.
 */
class QueueFacade(
    player: ExoPlayer,
    playbackSettings: PlaybackSettings,
    songUriResolver: SongUriResolver,
    /** Where new queue entries are built: off the main thread, as a long queue takes a while. */
    buildContext: CoroutineContext = Dispatchers.Default,
    /** The player the app plays through: [player] itself, or a Cast player around it. */
    activePlayer: Player = player
) : QueueOperations {
    private val playerThread = PlayerThread(player)

    private val publisher = QueueStatePublisher(player, songUriResolver).also(player::addListener)

    private val editor = PlaylistEditor(player, activePlayer, playbackSettings, songUriResolver, publisher)

    private val builder = QueueBuilder(buildContext)

    override val shuffleModeFlow: StateFlow<ShuffleMode> = publisher.shuffleModeFlow

    override val repeatModeFlow: StateFlow<RepeatMode> = publisher.repeatModeFlow

    override val queueStateFlow: StateFlow<QueueState> = publisher.queueStateFlow

    override var hasRestoredQueue: Boolean
        get() = publisher.isRestored
        set(value) {
            publisher.isRestored = value
            playerThread.run(publisher::publish)
        }

    override suspend fun setQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int
    ): Boolean = builder.buildThenApply({ NewQueue.build(songs, shuffleSongs, position) }) { queue -> editor.setQueue(queue) }

    override suspend fun buildQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int
    ): NewQueue = builder.build(songs, shuffleSongs, position)

    override fun setQueueIfContentVersion(
        contentVersion: Long,
        queue: NewQueue,
        shuffleMode: ShuffleMode
    ): Long? {
        check(playerThread.isCurrent) { "setQueueIfContentVersion is main thread only" }
        if (queueStateFlow.value.contentVersion != contentVersion) return null
        editor.setQueue(queue, shuffleMode)
        return queueStateFlow.value.contentVersion
    }

    override fun getQueue(): List<QueueItem> = queueStateFlow.value.items

    override fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> = publisher.lists.get(shuffleMode)

    override fun getCurrentItem(): QueueItem? = queueStateFlow.value.currentItem

    override fun getCurrentPosition(): Int? = queueStateFlow.value.currentPosition

    override fun getSize(): Int = publisher.lists.base.size

    override fun setCurrentItem(currentItem: QueueItem) {
        playerThread.run { editor.setCurrent(currentItem.uid) }
    }

    /**
     * The item after the current one, as the player would play it. [ignoreRepeat] treats the repeat mode as
     * [RepeatMode.All].
     */
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = nextIndex(if (ignoreRepeat) RepeatMode.All else repeatModeFlow.value)?.let { publisher.lists.base.getOrNull(it) }

    override fun getPrevious(): QueueItem? {
        val state = queueStateFlow.value
        val position = state.currentPosition ?: return null
        return state.items.getOrNull(position - 1)
    }

    /** The playlist index of the item after the current one under [repeatMode], or null if there's none. */
    private fun nextIndex(repeatMode: RepeatMode): Int? {
        val lists = publisher.lists
        val state = queueStateFlow.value
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
        playerThread.run { editor.seekTo(next) }
        return true
    }

    override fun skipToPrevious() {
        Timber.v("skipToPrevious()")
        getPrevious()?.let(::setCurrentItem) ?: Timber.v("No previous track to skip to")
    }

    override fun skipTo(position: Int) {
        getQueue().getOrNull(position)?.let(::setCurrentItem) ?: Timber.e("Couldn't skip to position $position, no associated queue item found")
    }

    override suspend fun addToQueue(songs: List<Song>): Boolean = builder.buildThenApply(newItems(songs)) { items -> editor.add(songs, items) }

    override suspend fun addToNext(songs: List<Song>): Boolean = builder.buildThenApply(newItems(songs)) { items -> editor.addNext(songs, items) }

    /** New entries for [songs], to build off the main thread. */
    private fun newItems(songs: List<Song>) = { songs.map { song -> song.toQueueEntry().toMediaItem() } }

    override fun updateSongs(songs: List<Song>) {
        val songsById = songs.associateBy { it.id }
        if (songsById.isEmpty()) return
        playerThread.run { editor.updateSongs(songsById) }
    }

    override fun move(
        from: Int,
        to: Int
    ) {
        playerThread.run { editor.move(from, to) }
    }

    override fun remove(items: List<QueueItem>) {
        val uids = items.map { it.uid }.toSet()
        playerThread.run { editor.remove(uids) }
    }

    override fun remove(song: Song) {
        remove(getQueue().filter { it.song.id == song.id })
    }

    override fun clear() {
        Timber.v("clear()")
        playerThread.run(editor::clear)
    }

    override fun getShuffleMode(): ShuffleMode = shuffleModeFlow.value

    override suspend fun setShuffleMode(
        shuffleMode: ShuffleMode,
        reshuffle: Boolean
    ) = withContext(Dispatchers.Main.immediate) { editor.setShuffleMode(shuffleMode, reshuffle) }

    override suspend fun toggleShuffleMode() = when (getShuffleMode()) {
        ShuffleMode.Off -> setShuffleMode(ShuffleMode.On, reshuffle = true)
        ShuffleMode.On -> setShuffleMode(ShuffleMode.Off, reshuffle = false)
    }

    override fun getRepeatMode(): RepeatMode = repeatModeFlow.value

    override fun setRepeatMode(repeatMode: RepeatMode) {
        playerThread.run { editor.setRepeatMode(repeatMode) }
    }

    override fun toggleRepeatMode() {
        when (getRepeatMode()) {
            RepeatMode.Off -> setRepeatMode(RepeatMode.All)
            RepeatMode.All -> setRepeatMode(RepeatMode.One)
            RepeatMode.One -> setRepeatMode(RepeatMode.Off)
        }
    }
}
