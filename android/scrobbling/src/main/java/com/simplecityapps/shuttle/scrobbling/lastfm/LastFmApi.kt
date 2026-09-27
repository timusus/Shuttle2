package com.simplecityapps.shuttle.scrobbling.lastfm

import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/** [Last.fm API](https://www.last.fm/api/authspec) root, everything else is a `method` form param. */
const val LASTFM_BASE_URL = "https://ws.audioscrobbler.com/"

interface LastFmApi {
    @FormUrlEncoded
    @POST("2.0/")
    suspend fun scrobble(
        @FieldMap params: Map<String, String>
    ): Response<LastFmScrobbleResponse>
}
