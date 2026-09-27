package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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

/** Jellyfin's playback reporting endpoints, which answer 204 No Content. */
class PlaybackReportingService(private val client: HttpClient) {
    suspend fun report(
        url: String,
        authorization: String,
        report: PlaybackReport
    ): NetworkResult<Unit> = client.networkResult {
        post(url) {
            header(HttpHeaders.Authorization, authorization)
            contentType(ContentType.Application.Json)
            setBody(report)
        }
    }

    suspend fun markPlayed(
        url: String,
        authorization: String,
        userId: String,
        datePlayed: String
    ): NetworkResult<Unit> = client.networkResult {
        post(url) {
            header(HttpHeaders.Authorization, authorization)
            parameter("userId", userId)
            parameter("datePlayed", datePlayed)
        }
    }
}
