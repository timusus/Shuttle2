package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

/** Plex's universal transcoder. Only the status matters, so the body is discarded. */
class TranscodeService(private val client: HttpClient) {
    /** Ends the transcode [session], which the server otherwise keeps running until it times out idle. */
    suspend fun stop(
        url: String,
        token: String,
        session: String
    ): NetworkResult<Unit> = client.networkResult {
        get(url) {
            header(PLEX_TOKEN, token)
            parameter("session", session)
        }
    }
}
