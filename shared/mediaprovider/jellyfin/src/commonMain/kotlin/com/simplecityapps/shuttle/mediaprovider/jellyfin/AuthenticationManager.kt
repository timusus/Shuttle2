package com.simplecityapps.shuttle.mediaprovider.jellyfin

import com.simplecityapps.shuttle.deviceinfo.DeviceInfo
import com.simplecityapps.shuttle.error.HttpStatusCode
import com.simplecityapps.shuttle.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.logging.LogPriority
import com.simplecityapps.shuttle.logging.logcat
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.AuthenticatedCredentials
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service.UserService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.*

class AuthenticationManager(
    private val userService: UserService,
    private val credentialStore: JellyfinPreferenceManager,
    private val deviceInfo: DeviceInfo
) {

    suspend fun authenticate(): Result<AuthenticatedCredentials> {
        val loginCredentials = credentialStore.getLoginCredentials().firstOrNull()
            ?: return Result.failure(Exception("Invalid login credentials"))

        val address = credentialStore.getAddress().firstOrNull()
            ?: return Result.failure(Exception("Invalid address"))

        logcat { "authenticate(address: $address)" }
        val authenticationResult = userService.authenticate(
            url = address,
            username = loginCredentials.username,
            password = loginCredentials.password,
            deviceName = deviceInfo.getDeviceName() ?: "Android",
            deviceId = deviceInfo.getDeviceId() ?: UUID.randomUUID().toString()
        )

        return authenticationResult.fold({ authenticationResponse ->
            val authenticatedCredentials = AuthenticatedCredentials(authenticationResponse.accessToken, authenticationResponse.user.id)
            credentialStore.setAuthenticatedCredentials(authenticatedCredentials)
            Result.success(authenticatedCredentials)
        }, { throwable ->
            // Todo: Ensure client actually maps to these error types
            (throwable as? RemoteServiceHttpError)?.let { error ->
                if (error.statusCode == HttpStatusCode.Unauthorized) {
                    credentialStore.setAuthenticatedCredentials(null)
                }
            }
            Result.failure(throwable)
        })
    }

    suspend fun buildJellyfinPath(itemId: String, authenticatedCredentials: AuthenticatedCredentials): String? {
        val address = credentialStore.getAddress().first()
        if (address == null) {
            logcat(LogPriority.WARN) { "Invalid jellyfin address (${address})" }
            return null
        }

        return "${address}" +
                "/Audio/$itemId" +
                "/universal" +
                "?UserId=${authenticatedCredentials.userId}" +
                "&DeviceId=${UUID.randomUUID()}" +
                "&PlaySessionId=${UUID.randomUUID()}" +
                "&Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg" +
                "&TranscodingContainer=ts" +
                "&TranscodingProtocol=hls" +
                "&EnableRedirection=true" +
                "&EnableRemoteMedia=true" +
                "&AudioCodec=aac" +
                "&api_key=${authenticatedCredentials.accessToken}"
    }
}