package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.time.Instant

/**
 * Reports plays through `scrobble`: a start as now playing (`submission=false`), a finished play as a play with the
 * time it was played (`submission=true`), which the server counts and passes on to its own scrobblers. Subsonic has
 * nothing for progress or a stop, so those report nothing.
 */
@Inject
class SubsonicPlaybackReporter(
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService
) : PlaybackReporter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Subsonic

    override suspend fun start(session: PlaybackSession, positionMs: Int): Boolean = scrobble(session.song, submission = false, timeMs = null)

    override suspend fun progress(session: PlaybackSession, positionMs: Int, paused: Boolean): Boolean = true

    override suspend fun stop(session: PlaybackSession, positionMs: Int): Boolean = true

    override suspend fun markPlayed(song: Song, playedAt: Instant): Boolean = scrobble(song, submission = true, timeMs = playedAt.toEpochMilliseconds())

    private suspend fun scrobble(song: Song, submission: Boolean, timeMs: Long?): Boolean {
        val id = song.externalId ?: return false
        return authenticationManager.request { address, auth -> service.scrobble(address, auth, id, submission, timeMs) } is NetworkResult.Success
    }
}
