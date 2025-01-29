package com.simplecityapps.provider.jellyfin.http

import com.jaredrummler.android.device.DeviceName
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
    deviceId: String,
    deviceName: String =
        try {
            DeviceName.getDeviceName()
        } catch (e: Exception) {
            "Unknown"
        },
    version: String = "1.0"
): NetworkResult<AuthenticationResult> = authenticateImpl(
    "$url/Users/AuthenticateByName",
    mapOf(
        "username" to username,
        "pw" to password
    ),
    "MediaBrowser Client=\"Shuttle2.0\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$version\""
)
