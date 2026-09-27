package com.simplecityapps.shuttle.scrobbling.queue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.scrobbling.worker.ScrobbleFlushWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Where a [com.simplecityapps.shuttle.scrobbling.ScrobblePlanner.Decision.Scrobble] lands: queued in Room so it
 * survives being offline or the process dying, then a unique flush job is (re-)scheduled to drain it.
 */
class ScrobbleQueue
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val scrobbleDao: ScrobbleDao
) {
    suspend fun enqueue(
        song: Song,
        startedAtEpochSec: Long
    ) {
        scrobbleDao.enqueue(
            QueuedScrobbleEntity(
                service = QueuedScrobbleEntity.SERVICE_LASTFM,
                artist = song.friendlyArtistName.orEmpty(),
                track = song.name.orEmpty(),
                album = song.album,
                albumArtist = song.albumArtist,
                durationMs = song.duration,
                startedAtEpochSec = startedAtEpochSec
            )
        )
        scrobbleDao.trimToNewest(QueuedScrobbleEntity.SERVICE_LASTFM, MAX_QUEUE_SIZE)
        scheduleFlush(context)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "scrobble_flush"

        /** Last.fm rejects a scrobble whose timestamp is this old ([show/track.scrobble] error code 3). */
        val MAX_AGE = TimeUnit.DAYS.toMillis(14)

        /** `track.scrobble` accepts at most this many per call ([show/track.scrobble](https://www.last.fm/api/show/track.scrobble)). */
        const val BATCH_SIZE = 50

        /** A queue that never flushes (offline, or signed out) still shouldn't grow without bound; the oldest go first. */
        const val MAX_QUEUE_SIZE = 5_000

        fun scheduleFlush(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScrobbleFlushWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
