package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** plex.tv sign-in. A two-factor code goes on the end of the password. */
class UserService(private val client: HttpClient) {
    suspend fun authenticate(
        username: String,
        password: String,
        authCode: String?
    ): NetworkResult<AuthenticationResult> = client.networkResult {
        post(SIGN_IN_URL) {
            // Retrofit sent this on every call, even a body-less POST like this one.
            contentType(ContentType.Application.Json)
            parameter("user[login]", username)
            parameter("user[password]", password + (authCode ?: ""))
        }
    }

    companion object {
        const val SIGN_IN_URL = "https://plex.tv/users/sign_in"
    }
}
