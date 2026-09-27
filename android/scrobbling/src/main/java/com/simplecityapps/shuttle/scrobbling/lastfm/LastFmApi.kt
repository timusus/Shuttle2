package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

/** [Last.fm API](https://www.last.fm/api/authspec) root, everything else is a `method` form param. */
const val LASTFM_BASE_URL = "https://ws.audioscrobbler.com/2.0/"

class LastFmApi(private val client: HttpClient) {
    suspend fun scrobble(params: Map<String, String>): NetworkResult<LastFmScrobbleResponse> = client.networkResult {
        submitForm(
            url = LASTFM_BASE_URL,
            formParameters = Parameters.build {
                params.forEach { (key, value) -> append(key, value) }
            }
        )
    }
}
