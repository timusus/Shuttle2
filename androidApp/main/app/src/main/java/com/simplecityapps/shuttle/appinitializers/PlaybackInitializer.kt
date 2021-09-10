package com.simplecityapps.shuttle.appinitializers

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.*
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Restores the queue when the app is launched. Saves the queue and queue position when they change.
 */
class PlaybackInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val playbackWatcher: PlaybackWatcher,
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    @Suppress("unused") private val castSessionManager: CastSessionManager,
    @Suppress("unused") private val mediaSessionManager: MediaSessionManager,
    @Suppress("unused") private val noiseManager: NoiseManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer,
    QueueChangeCallback,
    PlaybackWatcherCallback {

    private var progress = 0

    private var initTime = 0L

    @SuppressLint("BinaryOperationInTimber")
    override fun init(application: Application) {
        initTime = System.currentTimeMillis()
        Timber.v("PlaybackInitializer.init()")

        queueWatcher.addCallback(this)
        playbackWatcher.addCallback(this)

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

    private suspend fun restoreQueue(queuePosition: Int?, seekPosition: Int) {
        val queueRestoreStartTime = System.currentTimeMillis()
        queuePosition?.let {
            val songIds = playbackPreferenceManager.queueIds?.split(',')?.map { id -> id.toLong() }.orEmpty()
            if (songIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val shuffleSongIds = playbackPreferenceManager.shuffleQueueIds?.split(',')?.map { id -> id.toLong() }
                    val allSongIds = songIds.toMutableSet()
                    allSongIds.addAll(shuffleSongIds.orEmpty())

                    val allSongs = songRepository.getSongs(SongQuery.SongIds(allSongIds.toList()))
                        .filterNotNull()
                        .firstOrNull()
                        .orEmpty()

                    val songOrderMap = songIds.withIndex().associate { songId -> songId.value to songId.index }
                    val songs = allSongs.sortedBy { song -> songOrderMap[song.id] }

                    val shuffleSongs = shuffleSongIds?.let {
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


    // QueueChangeCallback Implementation

    override fun onQueueChanged(reason: QueueChangeCallback.QueueChangeReason) {
        playbackPreferenceManager.queueIds = queueManager.getQueue(QueueManager.ShuffleMode.Off)
            .map { queueItem -> queueItem.song.id }
            .joinToString(",")

        playbackPreferenceManager.shuffleQueueIds = queueManager.getQueue(QueueManager.ShuffleMode.On)
            .map { queueItem -> queueItem.song.id }
            .joinToString(",")
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        playbackPreferenceManager.queuePosition = newPosition
    }

    override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
        playbackPreferenceManager.shuffleMode = shuffleMode
    }

    override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        playbackPreferenceManager.repeatMode = repeatMode
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        when (playbackState) {
            is PlaybackState.Playing -> {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            }
            is PlaybackState.Paused -> {
                playbackPreferenceManager.playbackPosition = playbackManager.getProgress()

                queueManager.getCurrentItem()?.song?.let { song ->
                    val playbackPosition = playbackManager.getProgress() ?: 0
                    appCoroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            songRepository.setPlaybackPosition(song, playbackPosition)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onTrackEnded(song: Song) {
        playbackPreferenceManager.playbackPosition = 0

        appCoroutineScope.launch {
            withContext(Dispatchers.IO) {
                songRepository.setPlaybackPosition(song, song.duration)
                songRepository.incrementPlayCount(song)
            }
        }
    }


    // ProgressCallback Implementation

    override fun onProgressChanged(position: Int, duration: Int, fromUser: Boolean) {
        if (progress == 0) {
            progress = position
        }

        // Saves the playback progress to shared prefs if it has changed by at least 1 second
        if (position - progress > 1000) {
            playbackPreferenceManager.playbackPosition = position
            progress = position
        }
    }
}