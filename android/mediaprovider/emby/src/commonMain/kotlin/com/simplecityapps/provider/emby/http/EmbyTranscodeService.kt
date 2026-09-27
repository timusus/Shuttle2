package com.simplecityapps.provider.emby.http

import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

/** Asks the server what it would stream for a playback URL, without fetching the stream. */
class EmbyTranscodeService(private val client: HttpClient) {
    /** The `Content-Type` the server answers a HEAD of [url] with; null when it answers with an error. */
    suspend fun contentType(url: String): String? {
        val response = client.head(url)
        return if (response.status.isSuccess()) response.headers[HttpHeaders.ContentType] else null
    }
}
