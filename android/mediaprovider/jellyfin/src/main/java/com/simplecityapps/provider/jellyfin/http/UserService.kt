package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface UserService {
    @POST
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun authenticateImpl(
        @Url url: String,
        @Body body: Map<String, String>,
        @Header("Authorization") header: String
    ): NetworkResult<AuthenticationResult>
}

suspend fun UserService.authenticate(
    url: String,
    username: String,
    password: String,
    deviceId: String
): NetworkResult<AuthenticationResult> = authenticateImpl(
    "$url/Users/AuthenticateByName",
    mapOf(
        "username" to username,
        "pw" to password
    ),
    mediaBrowserAuthorization(deviceId)
)
