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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

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
    @Suppress("unused") private val noiseManager: NoiseManager
) : AppInitializer,
    QueueChangeCallback,
    PlaybackWatcherCallback {

    private var progress = 0

    @SuppressLint("BinaryOperationInTimber")
    override fun init(application: Application) {

        queueWatcher.addCallback(this)
        playbackWatcher.addCallback(this)

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
            val songIds = playbackPreferenceManager.queueIds?.split(",")?.map { id -> id.toLong() }
            val shuffleSongIds = playbackPreferenceManager.shuffleQueueIds?.split(",")?.map { id -> id.toLong() }
            val allSongIds = songIds.orEmpty().toMutableSet()
            allSongIds.addAll(shuffleSongIds.orEmpty())

            if (songIds.isNullOrEmpty()) {
                onRestoreComplete()
                return
            }

            songRepository.getSongs(SongQuery.SongIds(allSongIds.toList()))
                .first(emptyList())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.load(
                            songIds.mapNotNull { songId -> songs.firstOrNull { song -> song.id == songId } },
                            shuffleSongIds?.mapNotNull { shuffleSongId -> songs.firstOrNull { song -> song.id == shuffleSongId } },
                            queuePosition
                        ) { result ->
                            result.onFailure { error -> Timber.e("Failed to load playback after reloading queue. Error: $error") }
                            result.onSuccess { didLoadFirst ->
                                if (didLoadFirst) {
                                    playbackManager.seekTo(seekPosition)
                                }
                            }
                        }
                        onRestoreComplete()
                    },
                    onError = { error ->
                        Timber.e(error, "Failed to reload queue")
                        onRestoreComplete()
                    })
        } ?: run {
            onRestoreComplete()
        }
    }

    private fun onRestoreComplete() {
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

    override fun onShuffleChanged() {
        playbackPreferenceManager.shuffleMode = queueManager.getShuffleMode()
    }

    override fun onRepeatChanged() {
        playbackPreferenceManager.repeatMode = queueManager.getRepeatMode()
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        if (isPlaying) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        } else {
            playbackPreferenceManager.playbackPosition = playbackManager.getProgress()

            queueManager.getCurrentItem()?.song?.let { song ->
                song.playbackPosition = playbackManager.getProgress() ?: 0
                songRepository.setPlaybackPosition(song, song.playbackPosition)
                    .subscribeOn(Schedulers.io())
                    .subscribeBy(onError = { throwable -> Timber.e(throwable) })
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onPlaybackComplete(song: Song) {
        playbackPreferenceManager.playbackPosition = 0

        songRepository.setPlaybackPosition(song, song.duration)
            .subscribeOn(Schedulers.io())
            .subscribeBy(onError = { throwable -> Timber.e(throwable) })

        songRepository.incrementPlayCount(song)
            .subscribeOn(Schedulers.io())
            .subscribeBy(onError = { throwable -> Timber.e(throwable) })
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