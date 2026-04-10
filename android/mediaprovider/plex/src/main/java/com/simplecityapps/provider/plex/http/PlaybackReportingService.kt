package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

interface PlaybackReportingService {
    @GET
    suspend fun reportTimeline(
        @Url url: String,
        @Header("X-Plex-Token") token: String,
        @Query("ratingKey") ratingKey: String,
        @Query("key") key: String,
        @Query("state") state: String,
        @Query("time") time: Long,
        @Query("duration") duration: Long
    ): NetworkResult<Unit>

    @GET
    suspend fun scrobble(
        @Url url: String,
        @Header("X-Plex-Token") token: String,
        @Query("key") key: String,
        @Query("identifier") identifier: String
    ): NetworkResult<Unit>
}

suspend fun PlaybackReportingService.timelineUpdate(
    serverUrl: String,
    token: String,
    ratingKey: String,
    key: String,
    state: PlaybackState,
    positionMs: Long,
    durationMs: Long
): NetworkResult<Unit> = reportTimeline(
    url = "$serverUrl/:/timeline",
    token = token,
    ratingKey = ratingKey,
    key = key,
    state = state.value,
    time = positionMs,
    duration = durationMs
)

suspend fun PlaybackReportingService.markPlayed(
    serverUrl: String,
    token: String,
    key: String,
    identifier: String
): NetworkResult<Unit> = scrobble(
    url = "$serverUrl/:/scrobble",
    token = token,
    key = key,
    identifier = identifier
)

enum class PlaybackState(val value: String) {
    PLAYING("playing"),
    PAUSED("paused"),
    STOPPED("stopped")
}
