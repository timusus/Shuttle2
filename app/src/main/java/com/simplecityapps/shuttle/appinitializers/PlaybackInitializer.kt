package com.simplecityapps.shuttle.appinitializers

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.*
import com.simplecityapps.playback.chromecast.CastSessionManager
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

/**
 * Restores the queue when the app is launched. Saves the queue and queue position when they change.
 */
class PlaybackInitializer @Inject constructor(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val playbackWatcher: PlaybackWatcher,
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    @Suppress("unused") private val castSessionManager: CastSessionManager,
    @Suppress("unused") private val mediaSessionManager: MediaSessionManager,
    @Suppress("unused") private val noiseManager: NoiseManager,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) : AppInitializer,
    QueueChangeCallback,
    PlaybackWatcherCallback {

    private var progress = 0

    @SuppressLint("BinaryOperationInTimber")
    override fun init(application: Application) {

        Timber.i("PlaybackInitializer.init()")

        queueWatcher.addCallback(this)
        playbackWatcher.addCallback(this)

        appCoroutineScope.launch {
            val seekPosition = playbackPreferenceManager.playbackPosition ?: 0
            val queuePosition = playbackPreferenceManager.queuePosition
            val shuffleMode = playbackPreferenceManager.shuffleMode
            val repeatMode = playbackPreferenceManager.repeatMode

            Timber.v(
                "\nRestoring queue position: $queuePosition" +
                        "\nseekPosition: $seekPosition" +
                        "\nshuffleMode: $shuffleMode" +
                        "\nrepeatMode: $repeatMode"
            )

            queueManager.setShuffleMode(shuffleMode, reshuffle = false)
            queueManager.setRepeatMode(repeatMode)

            queuePosition?.let {
                withContext(Dispatchers.IO) {
                    val songIds = playbackPreferenceManager.queueIds?.split(',')?.map { id -> id.toLong() }
                    if (songIds.isNullOrEmpty()) {
                        Timber.i("Queue restoration failed: no queue to restore (songIds.size: ${songIds?.size})")
                        withContext(Dispatchers.Main) {
                            onRestoreComplete()
                        }
                    } else {
                        val shuffleSongIds = playbackPreferenceManager.shuffleQueueIds?.split(',')?.map { id -> id.toLong() }
                        val allSongIds = songIds.orEmpty().toMutableSet()
                        allSongIds.addAll(shuffleSongIds.orEmpty())

                        val allSongs = songRepository.getSongs(SongQuery.SongIds(allSongIds.toList()))
                            .firstOrNull()
                            .orEmpty()

                        val songOrderMap = songIds.withIndex().associate { songId -> songId.value to songId.index }
                        val songs = allSongs.sortedBy { song -> songOrderMap[song.id] }

                        val shuffleSongs = shuffleSongIds?.let {
                            val shuffleSongOrderMap = shuffleSongIds.withIndex().associate { songId -> songId.value to songId.index }
                            allSongs.sortedBy { song -> shuffleSongOrderMap[song.id] }
                        }

                        withContext(Dispatchers.Main) {
                            if (queueManager.setQueue(
                                    songs = songs,
                                    shuffleSongs = shuffleSongs,
                                    position = queuePosition
                                )
                            ) {
                                playbackManager.load { result ->
                                    result.onFailure { error -> Timber.e("Failed to load playback after reloading queue. Error: $error") }
                                    result.onSuccess { didLoadFirst ->
                                        if (didLoadFirst) {
                                            playbackManager.seekTo(seekPosition)
                                        }
                                    }
                                }
                            }
                            onRestoreComplete()
                        }
                    }
                }
            } ?: run {
                Timber.i("Queue restoration failed: queue position null")
                onRestoreComplete()
            }
        }
    }

    private fun onRestoreComplete() {
        Timber.i("Queue restoration complete")
        queueWatcher.hasRestoredQueue = true
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        if (queueWatcher.hasRestoredQueue) {
            playbackPreferenceManager.queueIds = queueManager.getQueue(QueueManager.ShuffleMode.Off)
                .map { queueItem -> queueItem.song.id }
                .joinToString(",")

            playbackPreferenceManager.shuffleQueueIds = queueManager.getQueue(QueueManager.ShuffleMode.On)
                .map { queueItem -> queueItem.song.id }
                .joinToString(",")

            playbackPreferenceManager.playbackPosition = null
        }
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        playbackPreferenceManager.queuePosition = newPosition

        if (queueWatcher.hasRestoredQueue) {
            playbackPreferenceManager.playbackPosition = null
        }
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
            is PlaybackState.Loading, PlaybackState.Playing -> {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            }
            else -> {
                playbackPreferenceManager.playbackPosition = playbackManager.getProgress()

                queueManager.getCurrentItem()?.song?.let { song ->
                    val playbackPosition = playbackManager.getProgress() ?: 0
                    song.playbackPosition = playbackPosition
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