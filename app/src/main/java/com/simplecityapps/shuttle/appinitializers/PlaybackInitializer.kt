package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
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
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val playbackPreferenceManager: PlaybackPreferenceManager
) : AppInitializer, QueueChangeCallback {


    override fun init(application: Application) {

        queueManager.addCallback(this)

        val queuePosition = playbackPreferenceManager.queuePosition
        queuePosition?.let { queuePosition ->
            val songIds = playbackPreferenceManager.queueIds?.split(",")?.map { id -> id.toLong() }
            songIds?.let { songIds ->
                songRepository.getSongs(SongQuery.SongIds(songIds)).first(emptyList()).subscribeBy(
                    onSuccess = { songs ->
                        playbackManager.load(songs, queuePosition, false)
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
    }

    override fun onQueuePositionChanged() {
        playbackPreferenceManager.queuePosition = queueManager.getCurrentPosition()
    }

    override fun onShuffleChanged() {

    }

    override fun onRepeatChanged() {

    }

}