package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.Body
import retrofit2.http.GET
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

    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun meImpl(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): NetworkResult<User>
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

/** The signed-in user, including their current `Policy` — used to refresh permissions that may have changed server-side. */
suspend fun UserService.me(
    url: String,
    authorization: String
): NetworkResult<User> = meImpl("$url/Users/Me", authorization)
