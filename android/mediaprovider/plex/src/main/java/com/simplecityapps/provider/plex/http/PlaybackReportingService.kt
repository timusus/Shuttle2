package com.simplecityapps.provider.plex.http

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/** Plex's player timeline and scrobble endpoints. Only the status matters, so the body is discarded. */
interface PlaybackReportingService {
    @GET
    suspend fun timeline(
        @Url url: String,
        @Header("X-Plex-Token") token: String,
        @Query("ratingKey") ratingKey: String,
        @Query("key") key: String,
        @Query("identifier") identifier: String,
        @Query("state") state: String,
        @Query("time") timeMs: Int,
        @Query("duration") durationMs: Int
    ): Response<Unit>

    @GET
    suspend fun scrobble(
        @Url url: String,
        @Header("X-Plex-Token") token: String,
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String
    ): Response<Unit>
}
