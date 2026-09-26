package com.simplecityapps.playback.persistence

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.tracing.trace
import androidx.tracing.traceAsync
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.PreparedQueue
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.QueueFacade
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.queue.queueEntries
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.playback.queue.shuffledIndices
import com.simplecityapps.playback.queue.toRepeatMode
import com.simplecityapps.playback.queue.toShuffleMode
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Keeps the queue and where playback is in it across restarts, in [playbackPreferenceManager]: saves them as the
 * players change them, and restores them when the app starts ([restore]). It's the only writer of the saved queue,
 * its position and song, the shuffle and repeat modes, and the position to resume from.
 *
 * The queue is [localPlayer]'s playlist, which holds the whole queue while casting too, so the queue, its position and
 * the modes are saved from its events: once per change, when the player reports the events of a change together
 * ([Player.Listener.onEvents]), so a queue set in several player calls is saved once, as it's left. Nothing is saved
 * until the queue first changes, so the empty queue the app starts with never replaces the saved one.
 *
 * Where playback is in the current item is [player]'s, the player the app plays through, which [PlaybackFacade][com.simplecityapps.playback.PlaybackFacade]
 * registers this listener on with the others. The position is saved within the player call that moves it (a seek, a
 * skip, playing on to the next item, a pause), since a play reads it back ([resumePosition]), and every
 * [SAVE_INTERVAL_MS] while playing. It's the current item's, so it's cleared when another item becomes current by a
 * skip or a queue change, and playing on to the next item saves that item's start. Nothing is saved while playback moves between devices, or plays something that isn't
 * in the queue, as a Cast receiver can.
 *
 * Main thread only, but for [restore], which any thread can start.
 */
class QueueStore(
    /** The player the app plays through: the local player, or a Cast player around it. */
    private val player: Player,
    /** The local player, whose playlist is the queue. */
    private val localPlayer: Player,
    private val queue: QueueFacade,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val songRepository: SongRepository,
    /** Runs on the main thread: the saves made while playing, and the restore. */
    private val scope: CoroutineScope,
    /** Where the saved queue is read from the library, off the main thread. */
    private val readContext: CoroutineContext = Dispatchers.IO
) : Player.Listener {
    /**
     * Whether playback is moving between devices: what the player reports then is the handover, not playback. Set by
     * the PlaybackFacade this is registered with, which follows the handover.
     */
    var isSwitching: () -> Boolean = { false }

    /** The queue's order as last saved (or as it was when this was created), to save it only when it changes. */
    private var savedOrder = QueueOrder.of(localPlayer)

    /** The queue as the shuffle mode presents it, and the current position in it, as last saved (or at creation). */
    private var savedPresentation = Presentation.of(localPlayer, savedOrder)

    /** What [saveNowPlaying] saved last, to leave an unchanged one alone. */
    private var savedNowPlaying: NowPlayingSnapshot? = null

    /** The uid of [player]'s current entry, as of the last item transition. */
    private var currentUid: Long? = player.currentMediaItem?.queueEntryOrNull?.uid

    private var playingSaves: Job? = null

    init {
        localPlayer.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events
                ) = saveQueue(events)
            }
        )
    }

    // The queue

    private fun saveQueue(events: Player.Events) {
        if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            playbackPreferenceManager.shuffleMode = localPlayer.shuffleModeEnabled.toShuffleMode()
        }
        if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
            playbackPreferenceManager.repeatMode = localPlayer.repeatMode.toRepeatMode()
        }
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            val order = QueueOrder.of(localPlayer)
            if (order != savedOrder) {
                savedOrder = order
                saveQueueIds(order)
            }
            val presentation = Presentation.of(localPlayer, order)
            if (presentation != savedPresentation) {
                savedPresentation = presentation
                saveQueuePosition(presentation)
            }
        }
    }

    /**
     * Saves the queue in both orders. Songs that aren't in the library (files opened from other apps) are left out:
     * there'd be nothing to restore them from, and their URI grants lapse with the app anyway. A queue that's what's
     * saved already (the one a restore just set) is left alone.
     */
    private fun saveQueueIds(order: QueueOrder) {
        val queueIds = order.entries.libraryIds()
        if (queueIds != playbackPreferenceManager.queueIds) playbackPreferenceManager.queueIds = queueIds
        val shuffleQueueIds = order.shuffled.map { index -> order.entries[index] }.libraryIds()
        if (shuffleQueueIds != playbackPreferenceManager.shuffleQueueIds) playbackPreferenceManager.shuffleQueueIds = shuffleQueueIds
    }

    private fun List<QueueEntry>.libraryIds(): String? = filter { entry -> entry.song.isInLibrary }.joinToString(",") { entry -> entry.song.id.toString() }.ifEmpty { null }

    /** Saves the position in the saved queue and the song it names. Which one that is depends on the songs before it too, once some are left out. */
    private fun saveQueuePosition(presentation: Presentation) {
        val savedPosition = savedQueuePosition(presentation.songs, presentation.currentPosition)
        playbackPreferenceManager.queuePosition = savedPosition?.position
        playbackPreferenceManager.restoreQueuePositionFromStart = savedPosition?.fromStart ?: false
        saveNowPlaying(savedPosition?.let { presentation.songs.filter { song -> song.isInLibrary }[it.position] })
    }

    /** Saves [song] as the one the saved position names, unless it's what was saved last. */
    private fun saveNowPlaying(song: Song?) {
        val snapshot = song?.let(NowPlayingSnapshot::of)
        if (snapshot != savedNowPlaying) {
            savedNowPlaying = snapshot
            playbackPreferenceManager.nowPlaying = snapshot
        }
    }

    // The position to resume from

    /** Where to resume [song] from: the saved position, else where the song itself says to start ([startOf]). */
    fun resumePosition(song: Song?): Int = playbackPreferenceManager.playbackPosition ?: song?.let(::startOf) ?: 0

    /**
     * Playback came back from a Cast receiver: saves the position the receiver was at, which the local player now
     * holds, in case the app is gone before playback pauses again. The local position of a receiver that never
     * reported one is kept, and a position of zero is never saved for it.
     */
    fun saveHandedBack() {
        if (player.currentMediaItem?.queueEntryOrNull == null) return
        player.currentPosition.takeIf { it > 0 }?.let { playbackPreferenceManager.playbackPosition = it.toInt() }
    }

    /** Saves where playback is in the current item, unless playback is moving between devices or on something not queued. */
    private fun saveCurrentPosition() {
        if (isSwitching() || player.currentMediaItem?.queueEntryOrNull == null) return
        playbackPreferenceManager.playbackPosition = player.currentPosition.toInt()
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        val uid = mediaItem?.queueEntryOrNull?.uid
        val previousUid = currentUid
        currentUid = uid
        // Another item became current by a skip or a queue change (not the first item of a queue set on an empty one,
        // whose saved position is a restore's own): it resumes from its own start (see [resumePosition]) until a
        // position is saved for it. Playing on to the next item saves that item's start instead (onPositionDiscontinuity).
        if (uid != previousUid && previousUid != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !isSwitching()) {
            playbackPreferenceManager.playbackPosition = null
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        // A seek, or playing on to the next item or back to the start on repeat. Not the first item of a queue set on an
        // empty one taking its place.
        if (oldPosition.mediaItem?.queueEntryOrNull != null) saveCurrentPosition()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        saveCurrentPosition()
        playingSaves?.cancel()
        playingSaves =
            if (isPlaying) {
                scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        saveCurrentPosition()
                    }
                }
            } else {
                null
            }
    }

    // The restore

    /**
     * Restores the saved shuffle and repeat modes straight away, so a request to play something before the queue is
     * restored finds them (a new queue can keep the shuffle mode). Then reads the saved queue and builds it off the main
     * thread, and sets it, loads it with [load] at the position to resume from, and marks the queue restored, in one
     * main thread step. Leaves the queue and playback alone if the queue changes other than by this restore before
     * then: a request to play something waits for the restore only so long (see PlayRequests), then sets its own queue.
     */
    fun restore(load: (positionMs: Int) -> Unit) {
        val shuffleMode = playbackPreferenceManager.shuffleMode
        val repeatMode = playbackPreferenceManager.repeatMode
        val seekPosition = playbackPreferenceManager.playbackPosition ?: 0
        val queuePosition = playbackPreferenceManager.queuePosition
        val initialContentVersion = queue.queueStateFlow.value.contentVersion

        // The restore doesn't rely on these: it sets the shuffle mode its saved position is in with the queue it sets.
        scope.launch(Dispatchers.Main.immediate) {
            queue.setShuffleMode(shuffleMode, reshuffle = false)
            queue.setRepeatMode(repeatMode)
        }

        scope.launch(readContext) {
            try {
                restoreQueue(shuffleMode, queuePosition, seekPosition, initialContentVersion, load)
            } finally {
                // Requests to play something else wait for it (see PlayRequests), so a restore that throws before its
                // main thread step mustn't leave them waiting.
                if (!queue.hasRestoredQueue) queue.hasRestoredQueue = true
            }
        }
    }

    private suspend fun restoreQueue(
        shuffleMode: ShuffleMode,
        queuePosition: Int?,
        seekPosition: Int,
        initialContentVersion: Long,
        load: (positionMs: Int) -> Unit
    ) {
        val timings = RestoreTimings()
        val savedQueue = if (queuePosition != null) {
            readSavedQueue(shuffleMode, queuePosition, timings)
        } else {
            Timber.w("Queue restoration failed: queue position null")
            null
        }
        val restoredSeekPosition = if (savedQueue?.fromStart == true) 0 else seekPosition

        val mainWait = TimeSource.Monotonic.markNow()
        withContext(Dispatchers.Main) {
            timings.add("main wait", mainWait)
            try {
                applyRestoredQueue(savedQueue, shuffleMode, initialContentVersion, seekPosition, restoredSeekPosition, load, timings)
            } finally {
                queue.hasRestoredQueue = true
            }
        }

        Timber.v("Queue restored in ${timings.total}ms (${timings.stages})")
    }

    /** The saved queue, built ready to set, or null if there's none or none of its songs are left. */
    private suspend fun readSavedQueue(
        shuffleMode: ShuffleMode,
        queuePosition: Int,
        timings: RestoreTimings
    ): SavedQueue? {
        val songIds = timings.measure("prefs") { playbackPreferenceManager.queueIds.toSongIds().orEmpty() }
        if (songIds.isEmpty()) return null
        val shuffleSongIds = timings.measure("prefs") { playbackPreferenceManager.shuffleQueueIds.toSongIds() }

        val songsById = timings.measureSuspending("DB") {
            songRepository.getSongs(SongQuery.SongIds((songIds + shuffleSongIds.orEmpty()).distinct()))
                .filterNotNull()
                .firstOrNull()
                .orEmpty()
                .associateBy { song -> song.id }
        }

        // A song gone from the library since the queue was saved is dropped, so the position is found again among
        // the songs that are left, in the list the shuffle mode presents.
        val songs = songIds.mapNotNull { songId -> songsById[songId] }
        val shuffleSongs = shuffleSongIds?.mapNotNull { songId -> songsById[songId] }
        val positionIds = if (shuffleMode == ShuffleMode.On && shuffleSongIds != null) shuffleSongIds else songIds
        val restoredPosition = restoredQueuePosition(positionIds, queuePosition, songsById.keys)
        if (restoredPosition == null) {
            Timber.w("Queue restoration failed: none of the saved songs are in the library")
            return null
        }

        return SavedQueue(
            queue = timings.measureSuspending("build") { queue.buildQueue(songs, shuffleSongs, restoredPosition.position) },
            fromStart = restoredPosition.fromStart || playbackPreferenceManager.restoreQueuePositionFromStart
        )
    }

    /**
     * Sets [savedQueue] with the [shuffleMode] its position is in, and loads the current song, on the main thread, where
     * nothing that sets the queue can come between the check and the set, or the set and the load.
     */
    private fun applyRestoredQueue(
        savedQueue: SavedQueue?,
        shuffleMode: ShuffleMode,
        initialContentVersion: Long,
        seekPosition: Int,
        restoredSeekPosition: Int,
        load: (positionMs: Int) -> Unit,
        timings: RestoreTimings
    ) {
        val changed = if (savedQueue != null) {
            timings.measure("setQueue") { queue.setQueueIfContentVersion(initialContentVersion, savedQueue.queue, shuffleMode) } == null
        } else {
            queue.queueStateFlow.value.contentVersion != initialContentVersion
        }
        if (changed) {
            Timber.w("The queue was set while it was being restored; the saved queue is dropped")
            return
        }

        if (queue.queueStateFlow.value.items.isEmpty()) {
            // Nothing to show for a queue that's gone.
            playbackPreferenceManager.nowPlaying = null
        }
        if (restoredSeekPosition != seekPosition) {
            // It's what a reload reads back as the position to resume from.
            playbackPreferenceManager.playbackPosition = restoredSeekPosition
        }
        timings.measure("load") { load(restoredSeekPosition) }
    }

    companion object {
        /** How often the position is saved while playing. */
        const val SAVE_INTERVAL_MS = 1_000L

        /** How far back a podcast or audiobook resumes from where it was left, so the listener catches the thread. */
        private const val SPOKEN_REWIND_MS = 5000

        /** Where [song] itself says to start: podcasts and audiobooks a little before where they were left, else 0. */
        fun startOf(song: Song): Int = if (song.type == Song.Type.Podcast || song.type == Song.Type.Audiobook) max(0, song.playbackPosition - SPOKEN_REWIND_MS) else 0
    }
}

/** The queue's entries in the player's order, and the playlist indices in shuffled order. */
private data class QueueOrder(
    val entries: List<QueueEntry>,
    val shuffled: List<Int>
) {
    companion object {
        fun of(player: Player) = QueueOrder(player.queueEntries(), player.shuffledIndices())
    }
}

/** The queue's songs as the shuffle mode presents them, and the current position in that list. */
private data class Presentation(
    val songs: List<Song>,
    val currentPosition: Int?
) {
    companion object {
        fun of(
            player: Player,
            order: QueueOrder
        ): Presentation {
            val current = player.currentMediaItemIndex.takeIf { order.entries.isNotEmpty() }
            val indices = if (player.shuffleModeEnabled) order.shuffled else order.entries.indices.toList()
            return Presentation(
                songs = indices.map { index -> order.entries[index].song },
                currentPosition = current?.let { indices.indexOf(it) }?.takeIf { it != -1 }
            )
        }
    }
}

/**
 * A saved queue, read and built ready to set.
 *
 * @param fromStart true when the song it starts at plays from the beginning rather than the saved position.
 */
private class SavedQueue(
    val queue: PreparedQueue,
    val fromStart: Boolean
)

/**
 * A queue position to save or restore.
 *
 * @param fromStart true when the song at [position] isn't the one that was playing, so the saved playback
 * position isn't its position and it starts from the beginning.
 */
internal data class QueuePosition(
    val position: Int,
    val fromStart: Boolean
)

/**
 * The position to save for [currentPosition] in [songs] (the queue as the shuffle mode presents it), once the
 * songs that aren't in the library are left out of the saved queue. When the current song is itself one of those,
 * it's the library song after it, or failing that the one before. Null when there's no position, or no library
 * song to save.
 */
internal fun savedQueuePosition(
    songs: List<Song>,
    currentPosition: Int?
): QueuePosition? = currentPosition?.let {
    remainingQueuePosition(songs.map { song -> song.isInLibrary }, currentPosition)
}

/**
 * The position to restore [savedPosition] in [savedIds] to, once the songs missing from [restoredIds] are dropped.
 * When the saved song is itself missing, it's the restored song after it, or failing that the one before. Null
 * when the position is out of range or nothing was restored.
 */
internal fun restoredQueuePosition(
    savedIds: List<Long>,
    savedPosition: Int,
    restoredIds: Set<Long>
): QueuePosition? = remainingQueuePosition(savedIds.map { songId -> songId in restoredIds }, savedPosition)

/** Where [position] lands once the items [kept] marks false are removed; see [savedQueuePosition]. */
private fun remainingQueuePosition(
    kept: List<Boolean>,
    position: Int
): QueuePosition? {
    if (position !in kept.indices) return null
    val keptCount = kept.count { it }
    if (keptCount == 0) return null
    val keptBefore = kept.take(position).count { it }
    return QueuePosition(position = minOf(keptBefore, keptCount - 1), fromStart = !kept[position])
}

private fun String?.toSongIds(): List<Long>? = this?.split(',')?.map { id -> id.toLong() }

/** How long each stage of a restore took, traced as `S2 restore <stage>` and summed into the restore's log line. */
private class RestoreTimings {
    private val start = TimeSource.Monotonic.markNow()
    private val stageMs = linkedMapOf<String, Long>()

    val total: Long get() = start.elapsedNow().inWholeMilliseconds

    val stages: String get() = stageMs.entries.joinToString { (stage, ms) -> "$stage ${ms}ms" }

    inline fun <T> measure(
        stage: String,
        crossinline block: () -> T
    ): T {
        val mark = TimeSource.Monotonic.markNow()
        return trace("S2 restore $stage", block).also { add(stage, mark) }
    }

    suspend inline fun <T> measureSuspending(
        stage: String,
        crossinline block: suspend () -> T
    ): T {
        val mark = TimeSource.Monotonic.markNow()
        return traceAsync("S2 restore $stage", stage.hashCode(), block).also { add(stage, mark) }
    }

    fun add(
        stage: String,
        mark: TimeSource.Monotonic.ValueTimeMark
    ) {
        stageMs[stage] = (stageMs[stage] ?: 0) + mark.elapsedNow().inWholeMilliseconds
    }
}
