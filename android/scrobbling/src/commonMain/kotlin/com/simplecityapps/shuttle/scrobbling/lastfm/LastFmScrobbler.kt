package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.Inject

/**
 * Acts on a [com.simplecityapps.shuttle.scrobbling.ScrobblePlanner] decision for Last.fm, and does nothing while
 * signed out or for a song without an artist or track. Now-playing is sent once and forgotten (bar an invalid
 * session, which signs the user out as the flush worker does); a scrobble is queued so it survives being offline.
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
        if (!song.isScrobblable()) return
        val result = client.updateNowPlaying(song, session.key)
        if (result is LastFmResult.Error && result.code == LastFmError.INVALID_SESSION) sessionStore.signOut()
    }

    suspend fun scrobble(
        song: Song,
        startedAtEpochSec: Long
    ) {
        if (sessionStore.session.value == null) return
        if (!song.isScrobblable()) return
        scrobbleQueue.enqueue(song, startedAtEpochSec)
    }

    /** Last.fm rejects a scrobble with no artist or track, so don't send or queue one. */
    private fun Song.isScrobblable() = !friendlyArtistName.isNullOrBlank() && !name.isNullOrBlank()
}
