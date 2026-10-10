package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
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

/** A [server]'s password sign-in and the signed-in user. */
class UserService(
    private val client: HttpClient,
    private val server: MediaBrowserServer,
    private val clientIdentity: ClientIdentity
) {
    suspend fun authenticate(
        url: String,
        username: String,
        password: String
    ): NetworkResult<AuthenticationResult> = client.networkResult {
        post("$url/Users/AuthenticateByName") {
            header(HttpHeaders.Authorization, mediaBrowserAuthorization(clientIdentity.id, deviceName = clientIdentity.deviceName, version = clientIdentity.version))
            contentType(ContentType.Application.Json)
            setBody(mapOf("username" to username, "pw" to password))
        }
    }

    /** The signed-in user, including their current `Policy` — used to refresh permissions that may have changed server-side. */
    suspend fun me(
        url: String,
        token: String
    ): NetworkResult<User> = client.networkResult {
        get("$url${server.apiPrefix}/Users/Me") {
            server.authorize(this, token, clientIdentity)
        }
    }
}
