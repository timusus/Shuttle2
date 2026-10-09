package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.Inject

/**
 * Acts on a [com.simplecityapps.shuttle.scrobbling.ScrobblePlanner] decision for ListenBrainz, as
 * [com.simplecityapps.shuttle.scrobbling.lastfm.LastFmScrobbler] does for Last.fm: nothing while signed out or for
 * a song without an artist or track; now-playing is sent once (a rejected token signs the user out), a scrobble is queued.
 */
class ListenBrainzScrobbler
@Inject
constructor(
    private val client: ListenBrainzClient,
    private val sessionStore: ListenBrainzSessionStore,
    private val scrobbleQueue: ScrobbleQueue
) {
    suspend fun nowPlaying(song: Song) {
        val account = sessionStore.account.value ?: return
        if (!song.isScrobblable()) return
        if (client.playingNow(song, account.token) == ListenBrainzResult.InvalidToken) sessionStore.signOut()
    }

    suspend fun scrobble(
        song: Song,
        startedAtEpochSec: Long
    ) {
        if (sessionStore.account.value == null) return
        if (!song.isScrobblable()) return
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ, song, startedAtEpochSec)
    }

    private fun Song.isScrobblable() = !friendlyArtistName.isNullOrBlank() && !name.isNullOrBlank()
}
