package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.provider.jellyfin.http.PlaybackReport
import com.simplecityapps.provider.jellyfin.http.PlaybackReportingService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Reports playback through Jellyfin's session endpoints. Jellyfin counts a play when it starts, and
 * marks the item played when it stops near the end.
 */
class JellyfinPlaybackReporter
@Inject
constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val playbackReportingService: PlaybackReportingService
) : PlaybackReporter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Jellyfin

    override suspend fun start(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean = report("Sessions/Playing", session, positionMs, paused = false)

    override suspend fun progress(
        session: PlaybackSession,
        positionMs: Int,
        paused: Boolean
    ): Boolean = report("Sessions/Playing/Progress", session, positionMs, paused)

    override suspend fun stop(
        session: PlaybackSession,
        positionMs: Int
    ): Boolean = report("Sessions/Playing/Stopped", session, positionMs, paused = false)

    override suspend fun markPlayed(
        song: Song,
        playedAt: Instant
    ): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val itemId = song.externalId ?: return false
        return playbackReportingService.markPlayed(
            url = "$address/UserPlayedItems/$itemId",
            authorization = authenticationManager.authorizationHeader(credentials),
            // Implied by a user's token, but required with an API key.
            userId = credentials.userId,
            datePlayed = playedAt.toString()
        ).isSuccessful
    }

    private suspend fun report(
        endpoint: String,
        session: PlaybackSession,
        positionMs: Int,
        paused: Boolean
    ): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val itemId = session.song.externalId ?: return false
        return playbackReportingService.report(
            url = "$address/$endpoint",
            authorization = authenticationManager.authorizationHeader(credentials),
            report = PlaybackReport(
                itemId = itemId,
                playSessionId = session.id,
                positionTicks = positionMs * TICKS_PER_MS,
                isPaused = paused
            )
        ).isSuccessful
    }

    private companion object {
        const val TICKS_PER_MS = 10_000L
    }
}
