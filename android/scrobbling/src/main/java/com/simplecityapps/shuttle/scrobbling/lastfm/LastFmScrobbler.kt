package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.Inject

/**
 * Acts on a [com.simplecityapps.shuttle.scrobbling.ScrobblePlanner] decision for Last.fm, and does nothing while
 * signed out. Now-playing is sent once and forgotten; a scrobble is queued so it survives being offline.
 */
class LastFmScrobbler
@Inject
constructor(
    private val client: LastFmClient,
    private val sessionStore: LastFmSessionStore,
    private val scrobbleQueue: ScrobbleQueue
) {
    suspend fun nowPlaying(song: Song) {
        val session = sessionStore.session.value ?: return
        client.updateNowPlaying(song, session.key)
    }

    suspend fun scrobble(
        song: Song,
        startedAtEpochSec: Long
    ) {
        if (sessionStore.session.value == null) return
        scrobbleQueue.enqueue(song, startedAtEpochSec)
    }
}
