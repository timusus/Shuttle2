package com.simplecityapps.provider.emby.http

import com.simplecityapps.mediaprovider.server.mediaBrowserAuthorization
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/** Emby's sign-in endpoint and the signed-in user. */
class UserService(private val client: HttpClient) {
    suspend fun authenticate(
        url: String,
        username: String,
        password: String,
        deviceId: String,
        deviceName: String,
        version: String
    ): NetworkResult<AuthenticationResult> = client.networkResult {
        post("$url/Users/AuthenticateByName") {
            header(HttpHeaders.Authorization, mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version))
            contentType(ContentType.Application.Json)
            setBody(mapOf("username" to username, "pw" to password))
        }
    }

    /** The signed-in user, including their current `Policy` — used to refresh permissions that may have changed server-side. */
    suspend fun me(
        url: String,
        token: String
    ): NetworkResult<User> = client.networkResult {
        get("$url/emby/Users/Me") {
            header(EMBY_TOKEN, token)
        }
    }
}

/** The header Emby reads a session's access token from. */
internal const val EMBY_TOKEN = "X-Emby-Token"

/** The header Emby reads the client identity from once signed in. */
internal const val EMBY_AUTHORIZATION = "X-Emby-Authorization"
