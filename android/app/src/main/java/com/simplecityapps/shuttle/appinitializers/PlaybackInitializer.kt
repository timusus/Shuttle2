package com.simplecityapps.shuttle.appinitializers

import android.annotation.SuppressLint
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.NoiseManager
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.abs
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
 * Pausing and track completion stay on [PlaybackWatcherCallback]: they save the position at that moment,
 * read live from the playback and queue, so they must run inside the call that reports them, and a
 * [kotlinx.coroutines.flow.StateFlow] can merge a pause into whatever follows it.
 */
class PlaybackInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val playbackWatcher: PlaybackWatcher,
    private val queueManager: QueueOperations,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    @Suppress("unused") private val castSessionManager: CastSessionManager,
    @Suppress("unused") private val mediaSessionManager: MediaSessionManager,
    @Suppress("unused") private val noiseManager: NoiseManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer,
    PlaybackWatcherCallback {
    /** The last position saved to preferences, so a save can be throttled by how far it's drifted. */
    private var lastSavedPosition: Int? = null

    private var initTime = 0L

    @SuppressLint("BinaryOperationInTimber")
    override fun init(application: Application) {
        initTime = System.currentTimeMillis()
        Timber.v("PlaybackInitializer.init()")

        playbackWatcher.addCallback(this)
        collectPlaybackState()

        val shuffleMode = playbackPreferenceManager.shuffleMode
        val repeatMode = playbackPreferenceManager.repeatMode
        val seekPosition = playbackPreferenceManager.playbackPosition ?: 0
        val queuePosition = playbackPreferenceManager.queuePosition

        appCoroutineScope.launch {
            queueManager.setShuffleMode(shuffleMode, reshuffle = false)
            queueManager.setRepeatMode(repeatMode)

            restoreQueue(queuePosition = queuePosition, seekPosition = seekPosition)
        }
    }

    private suspend fun restoreQueue(
        queuePosition: Int?,
        seekPosition: Int
    ) {
        val queueRestoreStartTime = System.currentTimeMillis()
        queuePosition?.let {
            val songIds = playbackPreferenceManager.queueIds?.split(',')?.map { id -> id.toLong() }.orEmpty()
            if (songIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val shuffleSongIds = playbackPreferenceManager.shuffleQueueIds?.split(',')?.map { id -> id.toLong() }
                    val allSongIds = songIds.toMutableSet()
                    allSongIds.addAll(shuffleSongIds.orEmpty())

                    val allSongs =
                        songRepository.getSongs(SongQuery.SongIds(allSongIds.toList()))
                            .filterNotNull()
                            .firstOrNull()
                            .orEmpty()

                    val songOrderMap = songIds.withIndex().associate { songId -> songId.value to songId.index }
                    val songs = allSongs.sortedBy { song -> songOrderMap[song.id] }

                    val shuffleSongs =
                        shuffleSongIds?.let {
                            val shuffleSongOrderMap = shuffleSongIds.withIndex().associate { songId -> songId.value to songId.index }
                            allSongs.sortedBy { song -> shuffleSongOrderMap[song.id] }
                        }

                    withContext(Dispatchers.Main) {
                        queueManager.setQueue(
                            songs = songs,
                            shuffleSongs = shuffleSongs,
                            position = queuePosition
                        )
                    }
                }
            }
        } ?: run {
            Timber.w("Queue restoration failed: queue position null")
        }

        Timber.v("Queue restored in ${System.currentTimeMillis() - queueRestoreStartTime}ms (Time since app init: ${System.currentTimeMillis() - initTime}ms)")

        playbackManager.load(seekPosition) {}

        queueManager.hasRestoredQueue = true
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

    private fun onQueueStateChanged(
        previous: QueueState,
        current: QueueState
    ) {
        if (current.contentVersion != previous.contentVersion) {
            playbackPreferenceManager.queueIds =
                queueManager.getQueue(QueueManager.ShuffleMode.Off)
                    .map { queueItem -> queueItem.song.id }
                    .joinToString(",")

            playbackPreferenceManager.shuffleQueueIds =
                queueManager.getQueue(QueueManager.ShuffleMode.On)
                    .map { queueItem -> queueItem.song.id }
                    .joinToString(",")
        }

        if (current.currentPosition != previous.currentPosition) {
            playbackPreferenceManager.queuePosition = current.currentPosition
        }
    }

    private fun startPlaybackService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
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
     * [PlaybackOperations.positionAnchorFlow], a pause, or a track change) always writes; otherwise the
     * write is throttled to once the position has drifted at least a second in either direction, so a
     * seek backwards or a restart is caught as readily as normal forward playback.
     */
    private fun saveProgress(
        position: Int,
        force: Boolean
    ) {
        val last = lastSavedPosition
        if (force || last == null || abs(position - last) >= 1000) {
            playbackPreferenceManager.playbackPosition = position
            lastSavedPosition = position
        }
    }

    /** Clears the saved position, and the throttle's baseline with it, so the next position is saved straight away. */
    private fun clearProgress() {
        playbackPreferenceManager.playbackPosition = null
        lastSavedPosition = null
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        // Playing is handled from playbackStateFlow. Pausing stays here: it reads the position and current song
        // live, so it must run before the call that reported it moves on (e.g. switchToPlayback() pauses the
        // old playback, then replaces it and reads the saved position back).
        if (playbackState is PlaybackState.Paused) {
            val position = playbackManager.getProgress()
            if (position != null) {
                saveProgress(position, force = true)
            } else {
                clearProgress()
            }

            queueManager.getCurrentItem()?.song?.let { song ->
                val playbackPosition = position ?: 0
                appCoroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        songRepository.setPlaybackPosition(song, playbackPosition)
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onTrackEnded(song: Song) {
        // The queue hasn't advanced yet at this point, so getNext() predicts whether it's about to -
        // matching the check PlaybackManager itself makes before it moves on. Only then is 0 the right
        // position for what's now the current item; otherwise (e.g. the last track with repeat off) the
        // queue stays on the song that just finished, and its saved position should stay too.
        if (queueManager.getNext() != null) {
            saveProgress(0, force = true)
        }

        appCoroutineScope.launch {
            withContext(Dispatchers.IO) {
                songRepository.setPlaybackPosition(song, song.duration)
                songRepository.incrementPlayCount(song)
            }
        }
    }
}
