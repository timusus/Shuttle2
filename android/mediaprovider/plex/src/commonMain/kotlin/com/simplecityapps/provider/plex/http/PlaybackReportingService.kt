package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

/** Plex's player timeline and scrobble endpoints. Only the status matters, so the body is discarded. */
class PlaybackReportingService(private val client: HttpClient) {
    suspend fun timeline(
        url: String,
        token: String,
        ratingKey: String,
        key: String,
        identifier: String,
        state: String,
        timeMs: Int,
        durationMs: Int
    ): NetworkResult<Unit> = client.networkResult {
        get(url) {
            header(PLEX_TOKEN, token)
            parameter("ratingKey", ratingKey)
            parameter("key", key)
            parameter("identifier", identifier)
            parameter("state", state)
            parameter("time", timeMs)
            parameter("duration", durationMs)
        }
    }

    suspend fun scrobble(
        url: String,
        token: String,
        ratingKey: String,
        identifier: String
    ): NetworkResult<Unit> = client.networkResult {
        get(url) {
            header(PLEX_TOKEN, token)
            parameter("key", ratingKey)
            parameter("identifier", identifier)
        }
    }
}
