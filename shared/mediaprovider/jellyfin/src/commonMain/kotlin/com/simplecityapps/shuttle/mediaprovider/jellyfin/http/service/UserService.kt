package com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service

import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.AuthenticationResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

@OptIn(InternalAPI::class)
class UserService(
    private val httpClient: HttpClient
) {
    private suspend fun authenticate(
        url: String,
        body: Map<String, String>,
        authorizationHeader: String
    ): Result<AuthenticationResponse> {
        return kotlin.runCatching {
            httpClient.get(url) {
                method = HttpMethod.Get
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append(HttpHeaders.Authorization, authorizationHeader)
                }
                this.body = body
            }
        }
    }

    suspend fun authenticate(
        url: String,
        username: String,
        password: String,
        deviceName: String,
        deviceId: String,
        version: String = "1.0"
    ): Result<AuthenticationResponse> {
        return authenticate(
            url = "$url/Users/AuthenticateByName",
            body = mapOf(
                "username" to username,
                "pw" to password
            ),
            authorizationHeader = "MediaBrowser Client=\"Shuttle2.0\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$version\""
        )
    }
}
