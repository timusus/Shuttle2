package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.mediaprovider.server.mediaBrowserAuthorization
import com.simplecityapps.mediaprovider.server.mediabrowser.AuthenticationResult
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/** Jellyfin's Quick Connect sign-in, which Emby doesn't have. */
class QuickConnectService(private val client: HttpClient) {
    suspend fun isQuickConnectEnabled(url: String): NetworkResult<Boolean> = client.networkResult {
        get("$url/QuickConnect/Enabled")
    }

    /** POSTs first; falls back to GET for older servers that reject the POST with 404/405. */
    suspend fun initiateQuickConnect(
        url: String,
        deviceId: String,
        deviceName: String,
        version: String
    ): NetworkResult<QuickConnectResult> {
        val authorization = mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version)
        val initiateUrl = "$url/QuickConnect/Initiate"
        val postResult =
            client.networkResult<QuickConnectResult> {
                post(initiateUrl) { header(HttpHeaders.Authorization, authorization) }
            }
        val error = (postResult as? NetworkResult.Failure)?.error as? RemoteServiceHttpError
        return if (error != null && error.httpStatusCode in setOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed)) {
            client.networkResult {
                get(initiateUrl) { header(HttpHeaders.Authorization, authorization) }
            }
        } else {
            postResult
        }
    }

    suspend fun pollQuickConnect(
        url: String,
        secret: String,
        deviceId: String,
        deviceName: String,
        version: String
    ): NetworkResult<QuickConnectResult> = client.networkResult {
        get("$url/QuickConnect/Connect") {
            parameter("secret", secret)
            header(HttpHeaders.Authorization, mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version))
        }
    }

    suspend fun authenticateWithQuickConnect(
        url: String,
        secret: String,
        deviceId: String,
        deviceName: String,
        version: String
    ): NetworkResult<AuthenticationResult> = client.networkResult {
        post("$url/Users/AuthenticateWithQuickConnect") {
            header(HttpHeaders.Authorization, mediaBrowserAuthorization(deviceId, deviceName = deviceName, version = version))
            contentType(ContentType.Application.Json)
            setBody(mapOf("Secret" to secret))
        }
    }
}
