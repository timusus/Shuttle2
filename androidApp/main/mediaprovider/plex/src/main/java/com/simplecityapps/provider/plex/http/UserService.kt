package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface UserService {
    @POST
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json",
        "X-Plex-Client-Identifier: Shuttle Music Player",
    )
    suspend fun authenticateImpl(
        @Url url: String,
        @Query("user[login]") login: String,
        @Query("user[password]") password: String
    ): NetworkResult<AuthenticationResult>
}

suspend fun UserService.authenticate(
    username: String,
    password: String,
    authCode: String?
): NetworkResult<AuthenticationResult> {
    return authenticateImpl(
        "https://plex.tv/users/sign_in",
        username,
        password + (authCode ?: "")
    )
}
