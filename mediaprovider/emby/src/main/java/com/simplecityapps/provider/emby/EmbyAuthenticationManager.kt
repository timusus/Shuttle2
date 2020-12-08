package com.simplecityapps.provider.emby

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.emby.http.*
import timber.log.Timber
import java.util.*

class EmbyAuthenticationManager(
    private val userService: UserService,
    private val credentialStore: CredentialStore
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

    fun setAddress(host: String, port: Int) {
        credentialStore.host = host
        credentialStore.port = port
    }

    fun getHost(): String? {
        return credentialStore.host
    }

    fun getPort(): Int? {
        return credentialStore.port
    }

    fun getAddress(): String? {
        return getHost()?.let { host ->
            getPort()?.toString()?.let { port ->
                "$host:$port"
            }
        }
    }

    suspend fun authenticate(address: String, loginCredentials: LoginCredentials): Result<AuthenticatedCredentials> {
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

    fun buildEmbyPath(itemId: String, authenticatedCredentials: AuthenticatedCredentials): String? {

        if (credentialStore.host == null || credentialStore.port == null) {
            Timber.w("Invalid emby address")
            return null
        }

        return "${credentialStore.host}:${credentialStore.port}/emby" +
                "/Audio/$itemId" +
                "/universal" +
                "?UserId=${authenticatedCredentials.userId}" +
                "&DeviceId=$deviceId" +
                "&PlaySessionId=${UUID.randomUUID()}" +
                "&MaxStreamingBitrate=140000000" +
                "&Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg" +
                "&TranscodingContainer=ts" +
                "&TranscodingProtocol=hls" +
                "&MaxSampleRate=48000" +
                "&EnableRedirection=true" +
                "&EnableRemoteMedia=true" +
                "&AudioCodec=aac" +
                "&api_key=${authenticatedCredentials.accessToken}"
    }
}