package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body of `/Sessions/Playing`, `/Sessions/Playing/Progress` and `/Sessions/Playing/Stopped`.
 * Jellyfin marks an item played when a stop arrives without a position, so [positionTicks] is always sent.
 */
@Serializable
data class PlaybackReport(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    // S2 streams through /Audio/{id}/universal, which serves the file as is when the container fits
    // and converts it otherwise; the server knows which, so this reports the stream rather than guessing.
    @SerialName("PlayMethod") val playMethod: String = "DirectStream"
)

/**
 * Reports playback through a [MediaBrowserServer]'s session endpoints, which answer 204 No Content. Jellyfin counts a
 * play when it starts, and marks the item played when it stops near the end. Marking played differs: Jellyfin takes
 * `POST /UserPlayedItems/{id}?userId=&datePlayed=` (the user id is implied by a user's token, but required with an API
 * key) with an ISO instant; Emby `POST /Users/{userId}/PlayedItems/{id}?DatePlayed=` with `yyyyMMddHHmmss`.
 */
class MediaBrowserPlaybackReporter(
    private val authenticationManager: MediaBrowserAuthenticationManager,
    private val client: HttpClient,
    private val clientIdentity: ClientIdentity
) : PlaybackReporter {
    private val server = authenticationManager.server

    override fun handles(song: Song): Boolean = song.mediaProvider == server.type

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
        val result: NetworkResult<Unit> = client.networkResult {
            when (server) {
                MediaBrowserServer.Jellyfin -> post("$address/UserPlayedItems/$itemId") {
                    server.authorizeSession(this, credentials.accessToken, clientIdentity)
                    parameter("userId", credentials.userId)
                    parameter("datePlayed", playedAt.toString())
                }

                MediaBrowserServer.Emby -> post("$address${server.apiPrefix}/Users/${credentials.userId}/PlayedItems/$itemId") {
                    server.authorizeSession(this, credentials.accessToken, clientIdentity)
                    parameter("DatePlayed", embyDatePlayed(playedAt))
                }
            }
        }
        return authenticationManager.checkSession(credentials, result) is NetworkResult.Success
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
        val report = PlaybackReport(
            itemId = itemId,
            playSessionId = session.id,
            positionTicks = positionMs * TICKS_PER_MS,
            isPaused = paused
        )
        val result: NetworkResult<Unit> = client.networkResult {
            post("$address${server.apiPrefix}/$endpoint") {
                server.authorizeSession(this, credentials.accessToken, clientIdentity)
                contentType(ContentType.Application.Json)
                setBody(report)
            }
        }
        return authenticationManager.checkSession(credentials, result) is NetworkResult.Success
    }

    private companion object {
        const val TICKS_PER_MS = 10_000L
    }
}

/** Emby's `DatePlayed` format: `yyyyMMddHHmmss`, in UTC. */
private fun embyDatePlayed(playedAt: Instant): String {
    val dateTime = playedAt.toLocalDateTime(TimeZone.UTC)
    return buildString {
        append(dateTime.year.toString().padStart(4, '0'))
        listOf(dateTime.month.ordinal + 1, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second).forEach { field ->
            append(field.toString().padStart(2, '0'))
        }
    }
}
