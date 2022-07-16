package com.simplecityapps.shuttle.mediaprovider.jellyfin

import com.benasher44.uuid.uuid4
import com.simplecityapps.shuttle.deviceinfo.DeviceInfo
import com.simplecityapps.shuttle.error.HttpStatusCode
import com.simplecityapps.shuttle.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.logging.LogPriority
import com.simplecityapps.shuttle.logging.logcat
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.AuthenticatedCredentials
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service.UserService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

class JellyfinAuthenticationManager(
    private val userService: UserService,
    private val preferenceManager: JellyfinPreferenceManager,
    private val deviceInfo: DeviceInfo
) {

    suspend fun authenticate(): Result<AuthenticatedCredentials> {
        val loginCredentials = preferenceManager.getLoginCredentials().firstOrNull()
            ?: return Result.failure(Exception("Invalid login credentials"))

        val address = preferenceManager.getAddress().firstOrNull()
            ?: return Result.failure(Exception("Invalid address"))

        logcat { "authenticate(address: $address)" }
        val authenticationResult = userService.authenticate(
            url = address,
            username = loginCredentials.username,
            password = loginCredentials.password,
            deviceName = deviceInfo.getDeviceName() ?: "Android",
            deviceId = deviceInfo.getDeviceId() ?: uuid4().toString()
        )

        return authenticationResult.fold({ authenticationResponse ->
            val authenticatedCredentials = AuthenticatedCredentials(authenticationResponse.accessToken, authenticationResponse.user.id)
            preferenceManager.setAuthenticatedCredentials(authenticatedCredentials)
            Result.success(authenticatedCredentials)
        }, { throwable ->
            // Todo: Ensure client actually maps to these error types
            (throwable as? RemoteServiceHttpError)?.let { error ->
                if (error.statusCode == HttpStatusCode.Unauthorized) {
                    preferenceManager.setAuthenticatedCredentials(null)
                }
            }
            Result.failure(throwable)
        })
    }

    suspend fun buildJellyfinPath(itemId: String, authenticatedCredentials: AuthenticatedCredentials): String? {
        val address = preferenceManager.getAddress().first()
        if (address == null) {
            logcat(LogPriority.WARN) { "Invalid jellyfin address (${address})" }
            return null
        }

        return "${address}" +
                "/Audio/$itemId" +
                "/universal" +
                "?UserId=${authenticatedCredentials.userId}" +
                "&DeviceId=${uuid4()}" +
                "&PlaySessionId=${uuid4()}" +
                "&Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg" +
                "&TranscodingContainer=ts" +
                "&TranscodingProtocol=hls" +
                "&EnableRedirection=true" +
                "&EnableRemoteMedia=true" +
                "&AudioCodec=aac" +
                "&api_key=${authenticatedCredentials.accessToken}"
    }
}