package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters

/** [Last.fm API](https://www.last.fm/api/authspec) root, everything else is a `method` form param. */
const val LASTFM_BASE_URL = "https://ws.audioscrobbler.com/2.0/"

/** The raw Last.fm calls, each taking its already-signed form params; [LastFmClient] builds and reads them. */
class LastFmApi(private val client: HttpClient) {
    suspend fun getToken(params: Map<String, String>): NetworkResult<LastFmTokenResponse> = client.networkResult { form(params) }

    suspend fun getSession(params: Map<String, String>): NetworkResult<LastFmSessionResponse> = client.networkResult { form(params) }

    suspend fun updateNowPlaying(params: Map<String, String>): NetworkResult<LastFmBasicResponse> = client.networkResult { form(params) }

    suspend fun scrobble(params: Map<String, String>): NetworkResult<LastFmScrobbleResponse> = client.networkResult { form(params) }

    private suspend fun form(params: Map<String, String>): HttpResponse = client.submitForm(
        url = LASTFM_BASE_URL,
        formParameters = Parameters.build {
            params.forEach { (key, value) -> append(key, value) }
        }
    )
}
