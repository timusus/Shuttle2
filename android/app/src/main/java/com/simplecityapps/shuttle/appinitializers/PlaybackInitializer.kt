package com.simplecityapps.shuttle.appinitializers

import android.annotation.SuppressLint
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.BitPerfectOutput
import com.simplecityapps.playback.NoiseManager
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
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
import dagger.Lazy
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
 * Track ends and pauses are events, collected from [PlaybackOperations.trackEndedFlow] and
 * [PlaybackOperations.pausePositionFlow] to record each song's own position. [PlaybackOperations] saves the
 * position to resume from on both itself, since it must be saved before the call reporting them returns.
 *
 * Also starts the playback components that run for the life of the app: Cast session handling, the media
 * session, the noisy-audio receiver and bit-perfect USB output.
 */
class PlaybackInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val castSessionManager: Lazy<CastSessionManager>,
    private val mediaSessionManager: Lazy<MediaSessionManager>,
    private val noiseManager: Lazy<NoiseManager>,
    private val bitPerfectOutput: Lazy<BitPerfectOutput>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    private var initTime = 0L

    @SuppressLint("BinaryOperationInTimber")
    override fun init(application: Application) {
        initTime = System.currentTimeMillis()
        Timber.v("PlaybackInitializer.init()")

        startPlaybackComponents()
        collectPlaybackState()
        collectSongPositions()

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

    /** Each starts itself when it's created, so creating it here is what starts it. */
    private fun startPlaybackComponents() {
        castSessionManager.get()
        mediaSessionManager.get()
        noiseManager.get()
        bitPerfectOutput.get()
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
