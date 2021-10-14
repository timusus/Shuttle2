package com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class TranscodeService(private val httpClient: HttpClient) {

    suspend fun transcode(url: String): HttpResponse {
        return httpClient.request(url) {
            method = HttpMethod.Head
        }
    }
}