package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface PlaybackReportingService {
    @POST
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun reportPlaybackStart(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Body body: PlaybackStartInfo
    ): NetworkResult<Unit>

    @POST
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun reportPlaybackProgress(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Body body: PlaybackProgressInfo
    ): NetworkResult<Unit>

    @POST
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun reportPlaybackStopped(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Body body: PlaybackStopInfo
    ): NetworkResult<Unit>
}

suspend fun PlaybackReportingService.playbackStart(
    serverUrl: String,
    token: String,
    itemId: String,
    sessionId: String,
    userId: String
): NetworkResult<Unit> = reportPlaybackStart(
    url = "$serverUrl/Sessions/Playing",
    token = token,
    body = PlaybackStartInfo(
        itemId = itemId,
        sessionId = sessionId,
        canSeek = true,
        isPaused = false,
        isMuted = false,
        volumeLevel = 100,
        playMethod = "DirectPlay",
        queueableMediaTypes = "Audio"
    )
)

suspend fun PlaybackReportingService.playbackProgress(
    serverUrl: String,
    token: String,
    itemId: String,
    sessionId: String,
    positionTicks: Long,
    isPaused: Boolean
): NetworkResult<Unit> = reportPlaybackProgress(
    url = "$serverUrl/Sessions/Playing/Progress",
    token = token,
    body = PlaybackProgressInfo(
        itemId = itemId,
        sessionId = sessionId,
        positionTicks = positionTicks,
        canSeek = true,
        isPaused = isPaused,
        isMuted = false,
        volumeLevel = 100,
        playMethod = "DirectPlay"
    )
)

suspend fun PlaybackReportingService.playbackStopped(
    serverUrl: String,
    token: String,
    itemId: String,
    sessionId: String,
    positionTicks: Long
): NetworkResult<Unit> = reportPlaybackStopped(
    url = "$serverUrl/Sessions/Playing/Stopped",
    token = token,
    body = PlaybackStopInfo(
        itemId = itemId,
        sessionId = sessionId,
        positionTicks = positionTicks
    )
)
