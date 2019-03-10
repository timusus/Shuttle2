package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject

/**
 * Restores the queue when the app is launched. Saves the queue and queue position when they change.
 */
class PlaybackInitializer @Inject constructor(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val playbackPreferenceManager: PlaybackPreferenceManager
) : AppInitializer,
    QueueChangeCallback,
    Playback.Callback,
    PlaybackManager.ProgressCallback {

    var progress = 0

    override fun init(application: Application) {

        queueManager.addCallback(this)
        playbackManager.addCallback(this)
        playbackManager.addProgressCallback(this)

        val seekPosition = playbackPreferenceManager.playbackPosition ?: 0
        val queuePosition = playbackPreferenceManager.queuePosition

        Timber.v("Restoring queue position: $queuePosition, seekPosition: $seekPosition")

        queuePosition?.let { queuePosition ->
            val songIds = playbackPreferenceManager.queueIds?.split(",")?.map { id -> id.toLong() }
            songIds?.let { songIds ->
                songRepository.getSongs(SongQuery.SongIds(songIds)).first(emptyList()).subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.load(songs, queuePosition, seekPosition, false)
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

        playbackPreferenceManager.playbackPosition = null
    }

    override fun onQueuePositionChanged() {
        playbackPreferenceManager.queuePosition = queueManager.getCurrentPosition()

        playbackPreferenceManager.playbackPosition = null
    }

    override fun onShuffleChanged() {

    }

    override fun onRepeatChanged() {

    }


    // Playback.Callback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        if (isPlaying) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        } else {
            playbackPreferenceManager.playbackPosition = playbackManager.getPosition()
        }
    }

    override fun onPlaybackPrepared() {

    }

    override fun onPlaybackComplete(song: Song?) {
        playbackPreferenceManager.playbackPosition = 0
    }


    // ProgressCallback Implementation

    override fun onProgressChanged(position: Int, total: Int) {

        // Saves the progress if it has changed by at least 1 second
        if (progress == 0) {
            progress = position
        }
        if (position - progress > 1000) {
            playbackPreferenceManager.playbackPosition = position
            progress = position
        }
    }
}