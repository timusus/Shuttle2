package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
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

    @GET
    @Headers("Accept: application/json")
    suspend fun quickConnectEnabledImpl(@Url url: String): NetworkResult<Boolean>

    @POST
    @Headers("Accept: application/json")
    suspend fun quickConnectInitiatePostImpl(
        @Url url: String,
        @Header("Authorization") header: String
    ): NetworkResult<QuickConnectResult>

    @GET
    @Headers("Accept: application/json")
    suspend fun quickConnectInitiateGetImpl(
        @Url url: String,
        @Header("Authorization") header: String
    ): NetworkResult<QuickConnectResult>

    @GET
    @Headers("Accept: application/json")
    suspend fun quickConnectConnectImpl(
        @Url url: String,
        @Header("Authorization") header: String
    ): NetworkResult<QuickConnectResult>

    @POST
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun authenticateWithQuickConnectImpl(
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
    deviceName: String,
    version: String
): NetworkResult<AuthenticationResult> = authenticateImpl(
    "$url/Users/AuthenticateByName",
    mapOf(
        "username" to username,
        "pw" to password
    ),
    mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version)
)

/** The signed-in user, including their current `Policy` — used to refresh permissions that may have changed server-side. */
suspend fun UserService.me(
    url: String,
    authorization: String
): NetworkResult<User> = meImpl("$url/Users/Me", authorization)

suspend fun UserService.isQuickConnectEnabled(url: String): NetworkResult<Boolean> = quickConnectEnabledImpl("$url/QuickConnect/Enabled")

/** POSTs first; falls back to GET for older servers that reject the POST with 404/405. */
suspend fun UserService.initiateQuickConnect(
    url: String,
    deviceId: String,
    deviceName: String,
    version: String
): NetworkResult<QuickConnectResult> {
    val header = mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version)
    val initiateUrl = "$url/QuickConnect/Initiate"
    val postResult = quickConnectInitiatePostImpl(initiateUrl, header)
    val error = (postResult as? NetworkResult.Failure)?.error as? RemoteServiceHttpError
    return if (error != null && error.httpStatusCode in setOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed)) {
        quickConnectInitiateGetImpl(initiateUrl, header)
    } else {
        postResult
    }
}

suspend fun UserService.pollQuickConnect(
    url: String,
    secret: String,
    deviceId: String,
    deviceName: String,
    version: String
): NetworkResult<QuickConnectResult> = quickConnectConnectImpl(
    "$url/QuickConnect/Connect?secret=$secret",
    mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version)
)

suspend fun UserService.authenticateWithQuickConnect(
    url: String,
    secret: String,
    deviceId: String,
    deviceName: String,
    version: String
): NetworkResult<AuthenticationResult> = authenticateWithQuickConnectImpl(
    "$url/Users/AuthenticateWithQuickConnect",
    mapOf("Secret" to secret),
    mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version)
)
