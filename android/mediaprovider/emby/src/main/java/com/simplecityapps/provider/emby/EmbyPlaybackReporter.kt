package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.provider.emby.http.PlaybackReport
import com.simplecityapps.provider.emby.http.PlaybackReportingService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Reports playback through Emby's session endpoints, which Jellyfin's are forked from. */
class EmbyPlaybackReporter
@Inject
constructor(
    private val authenticationManager: EmbyAuthenticationManager,
    private val playbackReportingService: PlaybackReportingService
) : PlaybackReporter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Emby

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
            url = "$address/emby/Users/${credentials.userId}/PlayedItems/$itemId",
            token = credentials.accessToken,
            authorization = authenticationManager.clientAuthorizationHeader(),
            datePlayed = embyDatePlayed(playedAt)
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
            url = "$address/emby/$endpoint",
            token = credentials.accessToken,
            authorization = authenticationManager.clientAuthorizationHeader(),
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

/** Emby's `DatePlayed` format: `yyyyMMddHHmmss`, in UTC. */
internal fun embyDatePlayed(playedAt: Instant): String {
    val dateTime = playedAt.toLocalDateTime(TimeZone.UTC)
    return buildString {
        append(dateTime.year.toString().padStart(4, '0'))
        listOf(dateTime.month.ordinal + 1, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second).forEach { field ->
            append(field.toString().padStart(2, '0'))
        }
    }
}
