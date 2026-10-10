package com.simplecityapps.provider.emby.http

import com.simplecityapps.mediaprovider.server.mediabrowser.EMBY_AUTHORIZATION
import com.simplecityapps.mediaprovider.server.mediabrowser.EMBY_TOKEN
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The body of `/Sessions/Playing`, `/Sessions/Playing/Progress` and `/Sessions/Playing/Stopped`; [positionTicks] is always sent. */
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

/** Emby's playback reporting endpoints, which answer 204 No Content. */
class PlaybackReportingService(private val client: HttpClient) {
    suspend fun report(
        url: String,
        token: String,
        authorization: String,
        report: PlaybackReport
    ): NetworkResult<Unit> = client.networkResult {
        post(url) {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
            contentType(ContentType.Application.Json)
            setBody(report)
        }
    }

    suspend fun markPlayed(
        url: String,
        token: String,
        authorization: String,
        datePlayed: String
    ): NetworkResult<Unit> = client.networkResult {
        post(url) {
            header(EMBY_TOKEN, token)
            header(EMBY_AUTHORIZATION, authorization)
            parameter("DatePlayed", datePlayed)
        }
    }
}
