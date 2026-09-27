package com.simplecityapps.provider.jellyfin.http

import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

/** Asks the server what it would stream for a playback URL, without fetching the stream. */
class JellyfinTranscodeService(private val client: HttpClient) {
    /** The `Content-Type` the server answers a HEAD of [url] with; null when it answers with an error. */
    suspend fun contentType(url: String): String? {
        // Retrofit's @HEAD call never asked for JSON; an explicit "*/*" stops ContentNegotiation
        // from adding "Accept: application/json" to a probe that isn't fetching a JSON body.
        val response = client.head(url) { header(HttpHeaders.Accept, "*/*") }
        return if (response.status.isSuccess()) response.headers[HttpHeaders.ContentType] else null
    }
}
