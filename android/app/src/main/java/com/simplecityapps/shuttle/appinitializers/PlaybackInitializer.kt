package com.simplecityapps.shuttle.appinitializers

import android.annotation.SuppressLint
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.tracing.trace
import androidx.tracing.traceAsync
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.BitPerfectOutput
import com.simplecityapps.playback.CastStarter
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.NewQueue
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Restores the queue when the app is launched. Saves the queue, queue position, shuffle and repeat modes and
 * playback position when they change, and starts [PlaybackService] when playback starts.
 *
 * State is collected from the playback and queue flows on [Dispatchers.Main.immediate], so a change made on
 * the main thread is handled before the call that made it returns, as the callbacks it replaced were. That
 * matters for the playback position, which [PlaybackOperations] reads back from the preferences.
 *
 * Track ends and pauses are events, collected from [PlaybackOperations.trackEndedFlow] and
 * [PlaybackOperations.pausePositionFlow] to record each song's own position. [PlaybackOperations] saves the
 * position to resume from on both itself, since it must be saved before the call reporting them returns.
 *
 * Also starts the playback components that run for the life of the app: Cast (once the app first comes to the
 * foreground), the media session and bit-perfect USB output.
 */
class PlaybackInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val castStarter: Lazy<CastStarter>,
    private val playRequests: Lazy<PlayRequests>,
    private val bitPerfectOutput: Lazy<BitPerfectOutput>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    private var initTime = 0L

    /**
     * The queue a restore sets, as the shuffle mode presents it, when it's every song that was saved: saving it again
     * would change nothing. Held until the next change to the queue's content is saved, or not. Main thread only.
     */
    private var unchangedRestoredQueue: List<Song>? = null

    /** What [saveNowPlaying] saved last, to leave an unchanged one alone. Main thread only. */
    private var savedNowPlaying: NowPlayingSnapshot? = null

    @SuppressLint("BinaryOperationInTimber")
    override fun init(application: Application) {
        initTime = System.currentTimeMillis()
        Timber.v("PlaybackInitializer.init()")

        startPlaybackComponents(application)
        collectPlaybackState()
        collectSongPositions()

        val shuffleMode = playbackPreferenceManager.shuffleMode
        val repeatMode = playbackPreferenceManager.repeatMode
        val seekPosition = playbackPreferenceManager.playbackPosition ?: 0
        val queuePosition = playbackPreferenceManager.queuePosition
        // A request to play something waits for the restore only so long (see PlayRequests), then sets its
        // own queue, and a restore finishing after that mustn't replace it.
        val initialContentVersion = queueManager.queueStateFlow.value.contentVersion

        // Set now, so a request played before the restore finishes finds them (a new queue can keep the shuffle mode).
        // The restore doesn't rely on it: it sets the shuffle mode its saved position is in with the queue it sets.
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            queueManager.setShuffleMode(shuffleMode, reshuffle = false)
            queueManager.setRepeatMode(repeatMode)
        }

        // Started off the main thread, which is busy with the app's start: only setting the queue and loading it
        // wait for it, in one step.
        appCoroutineScope.launch(Dispatchers.IO) {
            try {
                restoreQueue(
                    shuffleMode = shuffleMode,
                    queuePosition = queuePosition,
                    seekPosition = seekPosition,
                    initialContentVersion = initialContentVersion
                )
            } finally {
                // Requests to play something else wait for it (see PlayRequests), so a restore that throws before
                // its main thread step mustn't leave them waiting.
                if (!queueManager.hasRestoredQueue) {
                    queueManager.hasRestoredQueue = true
                }
            }
        }
    }

    /** Each starts itself when it's created, so creating it here is what starts it; Cast waits for the foreground. */
    private fun startPlaybackComponents(application: Application) {
        castStarter.get().startInForeground(application)
        playRequests.get().launchPlaybackFailureMessages()
        bitPerfectOutput.get()
    }

    /**
     * Reads the saved queue and builds it off the main thread, then sets it with [shuffleMode], loads it and marks the
     * queue restored in one main thread step. Leaves the queue and playback alone if the queue changes from [initialContentVersion]
     * other than by this restore: something else was played before the restore finished.
     */
    private suspend fun restoreQueue(
        shuffleMode: QueueManager.ShuffleMode,
        queuePosition: Int?,
        seekPosition: Int,
        initialContentVersion: Long
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
                applyRestoredQueue(savedQueue, shuffleMode, initialContentVersion, seekPosition, restoredSeekPosition, timings)
            } finally {
                queueManager.hasRestoredQueue = true
            }
        }

        Timber.v("Queue restored in ${timings.total}ms (${timings.stages}) (Time since app init: ${System.currentTimeMillis() - initTime}ms)")
    }

    /** The saved queue, built ready to set, or null if there's none or none of its songs are left. */
    private suspend fun readSavedQueue(
        shuffleMode: QueueManager.ShuffleMode,
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
        val positionIds = if (shuffleMode == QueueManager.ShuffleMode.On && shuffleSongIds != null) shuffleSongIds else songIds
        val restoredPosition = restoredQueuePosition(positionIds, queuePosition, songsById.keys)
        if (restoredPosition == null) {
            Timber.w("Queue restoration failed: none of the saved songs are in the library")
            return null
        }
        val restoredWhole = songs.size == songIds.size && shuffleSongs != null && shuffleSongs.size == shuffleSongIds.size

        return SavedQueue(
            queue = timings.measureSuspending("build") { queueManager.buildQueue(songs, shuffleSongs, restoredPosition.position) },
            fromStart = restoredPosition.fromStart || playbackPreferenceManager.restoreQueuePositionFromStart,
            unchanged = when {
                !restoredWhole -> null
                shuffleMode == QueueManager.ShuffleMode.On -> shuffleSongs
                else -> songs
            }
        )
    }

    /**
     * Sets [savedQueue] with the [shuffleMode] its position is in, and loads the current song, on the main thread, where
     * nothing that sets the queue can come between the check and the set, or the set and the load.
     */
    private fun applyRestoredQueue(
        savedQueue: SavedQueue?,
        shuffleMode: QueueManager.ShuffleMode,
        initialContentVersion: Long,
        seekPosition: Int,
        restoredSeekPosition: Int,
        timings: RestoreTimings
    ) {
        if (savedQueue != null) {
            unchangedRestoredQueue = savedQueue.unchanged
            val restoredContentVersion = timings.measure("setQueue") {
                queueManager.setQueueIfContentVersion(initialContentVersion, savedQueue.queue, shuffleMode)
            }
            if (restoredContentVersion == null) {
                unchangedRestoredQueue = null
                Timber.w("The queue was set while it was being restored; the saved queue is dropped")
                return
            }
        } else if (queueManager.queueStateFlow.value.contentVersion != initialContentVersion) {
            Timber.w("The queue was set while it was being restored; the saved queue is dropped")
            return
        }

        if (queueManager.queueStateFlow.value.items.isEmpty()) {
            // Nothing to show for a queue that's gone.
            playbackPreferenceManager.nowPlaying = null
        }
        if (restoredSeekPosition != seekPosition) {
            // It's what a reload reads back as the position to resume from.
            playbackPreferenceManager.playbackPosition = restoredSeekPosition
        }
        // A saved song that can't load (a server out of reach, a file not there yet) stays where it was left.
        timings.measure("load") { playbackManager.load(restoredSeekPosition, skipUnloadable = false) {} }
    }

    /**
     * Each flow is compared against a snapshot taken here rather than its value when collection starts, so
     * its current value isn't handled as a change (it's what the preferences were just read from, or the
     * initial state), and a change made in between isn't missed.
     */
    private fun collectPlaybackState() {
        val queueState = queueManager.queueStateFlow.value
        val shuffleMode = queueManager.shuffleModeFlow.value
        val repeatMode = queueManager.repeatModeFlow.value
        val playbackState = playbackManager.playbackStateFlow.value
        val progress = playbackManager.progressFlow.value
        val positionAnchor = playbackManager.positionAnchorFlow.value

        appCoroutineScope.launchCollectingChanges(queueManager.queueStateFlow, queueState, Dispatchers.Main.immediate) { previous, current ->
            onQueueStateChanged(previous, current)
        }
        appCoroutineScope.launchCollectingChanges(queueManager.shuffleModeFlow, shuffleMode, Dispatchers.Main.immediate) { _, current ->
            playbackPreferenceManager.shuffleMode = current
        }
        appCoroutineScope.launchCollectingChanges(queueManager.repeatModeFlow, repeatMode, Dispatchers.Main.immediate) { _, current ->
            playbackPreferenceManager.repeatMode = current
        }
        appCoroutineScope.launchCollectingChanges(playbackManager.playbackStateFlow, playbackState, Dispatchers.Main.immediate) { _, current ->
            if (current is PlaybackState.Playing) {
                startPlaybackService()
            }
        }
        appCoroutineScope.launchCollectingChanges(playbackManager.progressFlow, progress, Dispatchers.Main.immediate) { _, current ->
            current?.let { saveProgress(it.position, force = false) }
        }
        // positionAnchorFlow republishes only on discontinuities (state changes, seeks, track changes,
        // speed changes, playback switches), so every emission is worth an immediate save - that's what
        // keeps the saved position current after a restart or a seek backwards, without a high-water mark.
        appCoroutineScope.launchCollectingChanges(playbackManager.positionAnchorFlow, positionAnchor, Dispatchers.Main.immediate) { _, current ->
            current.positionMs?.let { saveProgress(it, force = true) }
        }
    }

    private fun collectSongPositions() {
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackManager.trackEndedFlow.collect { song -> recordPlayedThrough(song) }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackManager.pausePositionFlow.collect { songPosition -> saveSongPosition(songPosition) }
        }
    }

    private fun onQueueStateChanged(
        previous: QueueState,
        current: QueueState
    ) {
        // Songs that aren't in the library (files opened from other apps) aren't saved: there'd be nothing
        // to restore them from, and their URI grants lapse with the app anyway.
        if (current.contentVersion != previous.contentVersion) {
            val savedAlready = current.presents(unchangedRestoredQueue)
            unchangedRestoredQueue = null
            if (!savedAlready) {
                playbackPreferenceManager.queueIds =
                    queueManager.getQueue(QueueManager.ShuffleMode.Off)
                        .filter { queueItem -> queueItem.song.isInLibrary }
                        .joinToString(",") { queueItem -> queueItem.song.id.toString() }

                playbackPreferenceManager.shuffleQueueIds =
                    queueManager.getQueue(QueueManager.ShuffleMode.On)
                        .filter { queueItem -> queueItem.song.isInLibrary }
                        .joinToString(",") { queueItem -> queueItem.song.id.toString() }
            }
        }

        // Which saved song the position names depends on the songs before it too, once some are left out.
        if (current.contentVersion != previous.contentVersion ||
            current.currentPosition != previous.currentPosition ||
            current.songDataVersion != previous.songDataVersion
        ) {
            val songs = current.items.map { queueItem -> queueItem.song }
            val savedPosition = savedQueuePosition(songs, current.currentPosition)
            playbackPreferenceManager.queuePosition = savedPosition?.position
            playbackPreferenceManager.restoreQueuePositionFromStart = savedPosition?.fromStart ?: false
            saveNowPlaying(savedPosition?.let { songs.filter { song -> song.isInLibrary }[it.position] })
        }
    }

    /** Saves [song] as the one the saved position names, unless it's what was saved last. */
    private fun saveNowPlaying(song: Song?) {
        val snapshot = song?.let(NowPlayingSnapshot::of)
        if (snapshot != savedNowPlaying) {
            savedNowPlaying = snapshot
            playbackPreferenceManager.nowPlaying = snapshot
        }
    }

    /** Whether these are the very [songs] (not just the same ones), in order: a queue set from them and nothing else. */
    private fun QueueState.presents(songs: List<Song>?): Boolean = songs != null && items.size == songs.size && items.indices.all { index -> items[index].song === songs[index] }

    private fun startPlaybackService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_START))
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Cannot start foreground service from background - likely audio focus regained while app in background")
            } else {
                throw e
            }
        }
    }

    /**
     * Saves the playback position to preferences. A [force]d save (a discontinuity from
     * [PlaybackOperations.positionAnchorFlow]) always writes; otherwise the write is throttled
     * to once the position has drifted at least a second in either direction from the saved one, so a
     * seek backwards or a restart is caught as readily as normal forward playback. The saved position is
     * read back rather than remembered, since [PlaybackOperations] saves one itself on a pause or track end.
     */
    private fun saveProgress(
        position: Int,
        force: Boolean
    ) {
        val saved = playbackPreferenceManager.playbackPosition
        if (force || saved == null || abs(position - saved) >= 1000) {
            playbackPreferenceManager.playbackPosition = position
        }
    }

    private fun saveSongPosition(songPosition: SongPosition) {
        appCoroutineScope.launch {
            withContext(Dispatchers.IO) {
                songRepository.setPlaybackPosition(songPosition.song, songPosition.positionMs)
            }
        }
    }

    private fun recordPlayedThrough(song: Song) {
        appCoroutineScope.launch {
            withContext(Dispatchers.IO) {
                songRepository.setPlaybackPosition(song, song.duration)
                songRepository.incrementPlayCount(song)
            }
        }
    }
}

/**
 * A saved queue, read and built ready to set.
 *
 * @param fromStart true when the song it starts at plays from the beginning rather than the saved position.
 * @param unchanged the queue as the shuffle mode presents it, when it's every song that was saved.
 */
private class SavedQueue(
    val queue: NewQueue,
    val fromStart: Boolean,
    val unchanged: List<Song>?
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
