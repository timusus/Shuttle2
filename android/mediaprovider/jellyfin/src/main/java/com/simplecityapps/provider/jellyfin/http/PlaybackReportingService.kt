package com.simplecityapps.provider.jellyfin.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The body of `/Sessions/Playing`, `/Sessions/Playing/Progress` and `/Sessions/Playing/Stopped`.
 * Jellyfin marks an item played when a stop arrives without a position, so [positionTicks] is always sent.
 */
@JsonClass(generateAdapter = true)
data class PlaybackReport(
    @Json(name = "ItemId") val itemId: String,
    @Json(name = "PlaySessionId") val playSessionId: String,
    @Json(name = "PositionTicks") val positionTicks: Long,
    @Json(name = "IsPaused") val isPaused: Boolean,
    @Json(name = "CanSeek") val canSeek: Boolean = true,
    // S2 streams through /Audio/{id}/universal, which serves the file as is when the container fits
    // and converts it otherwise; the server knows which, so this reports the stream rather than guessing.
    @Json(name = "PlayMethod") val playMethod: String = "DirectStream"
)

// Both endpoints answer 204 No Content, which NetworkResult treats as a failure, so these return the raw Response.
interface PlaybackReportingService {
    @POST
    suspend fun report(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body report: PlaybackReport
    ): Response<Unit>

    @POST
    suspend fun markPlayed(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String,
        @Query("datePlayed") datePlayed: String
    ): Response<Unit>
}
