package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.*
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
    @Suppress("unused") private val mediaSessionManager: MediaSessionManager,
    @Suppress("unused") private val noiseManager: NoiseManager
) : AppInitializer,
    QueueChangeCallback,
    PlaybackWatcherCallback {

    private var progress = 0

    private var hasRestoredPlaybackPosition = false

    override fun init(application: Application) {

        queueWatcher.addCallback(this)
        playbackWatcher.addCallback(this)

        val seekPosition = playbackPreferenceManager.playbackPosition ?: 0
        val queuePosition = playbackPreferenceManager.queuePosition

        Timber.v("Restoring queue position: $queuePosition, seekPosition: $seekPosition")

        queuePosition?.let { queuePosition ->
            val songIds = playbackPreferenceManager.queueIds?.split(",")?.map { id -> id.toLong() }
            songIds?.let { songIds ->
                songRepository.getSongs(SongQuery.SongIds(songIds))
                    .first(emptyList())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.load(songs, queuePosition, seekPosition, false)
                        hasRestoredPlaybackPosition = true
                    },
                    onError = { error ->
                        Timber.e(error, "Failed to reload queue")
                    })
            }
        }
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        playbackPreferenceManager.queueIds = queueManager.getQueue()
            .map { queueItem -> queueItem.song.id }
            .joinToString(",")

        if (hasRestoredPlaybackPosition) {
            playbackPreferenceManager.playbackPosition = null
        }
    }

    override fun onQueuePositionChanged() {
        playbackPreferenceManager.queuePosition = queueManager.getCurrentPosition()

        if (hasRestoredPlaybackPosition) {
            playbackPreferenceManager.playbackPosition = null
        }
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        if (isPlaying) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        } else {
            playbackPreferenceManager.playbackPosition = playbackManager.getPosition()

            queueManager.getCurrentItem()?.song?.let { song ->
                song.playbackPosition = playbackManager.getPosition() ?: 0
                songRepository.setPlaybackPosition(song, song.playbackPosition)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            }
        }
    }

    override fun onPlaybackComplete(song: Song) {
        playbackPreferenceManager.playbackPosition = 0

        songRepository.setPlaybackPosition(song, song.duration)
            .subscribeOn(Schedulers.io())
            .subscribe()

        songRepository.incrementPlayCount(song)
            .subscribeOn(Schedulers.io())
            .subscribe()
    }


    // ProgressCallback Implementation

    override fun onProgressChanged(position: Int, total: Int) {

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