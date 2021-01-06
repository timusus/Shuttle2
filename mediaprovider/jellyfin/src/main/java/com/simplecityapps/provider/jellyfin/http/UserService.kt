package com.simplecityapps.provider.jellyfin.http

import com.jaredrummler.android.device.DeviceName
import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.*

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
    deviceName: String = DeviceName.getDeviceName(),
    version: String = "1.0"
): NetworkResult<AuthenticationResult> {
    return authenticateImpl(
        "$url/Users/AuthenticateByName",
        mapOf(
            "username" to username,
            "pw" to password
        ),
        "MediaBrowser Client=\"Shuttle2.0\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$version\""
    )
}

