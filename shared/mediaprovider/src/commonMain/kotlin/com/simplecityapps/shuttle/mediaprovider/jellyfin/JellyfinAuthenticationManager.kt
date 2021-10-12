package com.simplecityapps.shuttle.mediaprovider.jellyfin

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.jellyfin.http.*
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.AuthenticatedCredentials
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data.LoginCredentials
import com.simplecityapps.shuttle.mediaprovider.jellyfin.http.service.UserService
import io.ktor.http.*
import timber.log.Timber
import java.util.*

class JellyfinAuthenticationManager(
    private val userService: UserService,
    private val credentialStore: JellyfinCredentialStore
) {

    private val deviceId = UUID.randomUUID().toString()

    fun getLoginCredentials(): LoginCredentials? {
        return credentialStore.loginCredentials
    }

    fun setLoginCredentials(loginCredentials: LoginCredentials?) {
        credentialStore.loginCredentials = loginCredentials
    }

    fun getAuthenticatedCredentials(): AuthenticatedCredentials? {
        return credentialStore.authenticatedCredentials
    }

    fun setAddress(address: String) {
        credentialStore.address = address
    }

    fun getAddress(): String? {
        return credentialStore.address
    }

    suspend fun authenticate(address: String, loginCredentials: LoginCredentials): Result<AuthenticatedCredentials> {
        Timber.d("authenticate(address: $address)")
        val authenticationResult = userService.authenticate(
            url = address,
            username = loginCredentials.username,
            password = loginCredentials.password,
            deviceId = deviceId
        )

        return when (authenticationResult) {
            is NetworkResult.Success<AuthenticationResult> -> {
                val authenticatedCredentials = AuthenticatedCredentials(authenticationResult.body.accessToken, authenticationResult.body.user.id)
                credentialStore.authenticatedCredentials = authenticatedCredentials
                Result.success(authenticatedCredentials)
            }
            is NetworkResult.Failure -> {
                (authenticationResult.error as? RemoteServiceHttpError)?.let { error ->
                    if (error.httpStatusCode == HttpStatusCode.Unauthorized) {
                        credentialStore.authenticatedCredentials = null
                    }
                }
                Result.failure(authenticationResult.error)
            }
        }
    }

    fun buildJellyfinPath(itemId: String, authenticatedCredentials: AuthenticatedCredentials): String? {
        if (credentialStore.address == null) {
            Timber.w("Invalid jellyfin address (${credentialStore.address})")
            return null
        }

        return "${credentialStore.address}" +
                "/Audio/$itemId" +
                "/universal" +
                "?UserId=${authenticatedCredentials.userId}" +
                "&DeviceId=$deviceId" +
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