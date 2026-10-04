package com.simplecityapps.shuttle.scrobbling.queue

import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.time.Duration.Companion.days

/**
 * Where a [com.simplecityapps.shuttle.scrobbling.ScrobblePlanner.Decision.Scrobble] lands: queued in Room so it
 * survives being offline or the process dying, then a flush is (re-)scheduled to drain it.
 */
class ScrobbleQueue
@Inject
constructor(
    private val scrobbleDao: ScrobbleDao,
    private val flushScheduler: ScrobbleFlushScheduler
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
        scheduleFlush()
    }

    /** Drops every queued Last.fm scrobble: signing out means they're never sent. */
    suspend fun clear() {
        scrobbleDao.deleteAll(QueuedScrobbleEntity.SERVICE_LASTFM)
    }

    /** (Re-)schedules the flush, at start-up and after a sign-in, for anything queued while it couldn't send. */
    fun scheduleFlush() {
        flushScheduler.scheduleFlush()
    }

    companion object {
        /** Last.fm rejects a scrobble whose timestamp is this old ([show/track.scrobble] error code 3). */
        val MAX_AGE = 14.days

        /** `track.scrobble` accepts at most this many per call ([show/track.scrobble](https://www.last.fm/api/show/track.scrobble)). */
        const val BATCH_SIZE = 50

        /** A queue that never flushes (offline, or signed out) still shouldn't grow without bound; the oldest go first. */
        const val MAX_QUEUE_SIZE = 5_000
    }
}
