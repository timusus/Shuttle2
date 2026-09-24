package com.simplecityapps.provider.emby

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.AuthenticationResult
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.provider.emby.http.authenticate
import java.util.UUID
import timber.log.Timber

class EmbyAuthenticationManager(
    private val userService: UserService,
    private val credentialStore: CredentialStore
) {
    private val deviceId = UUID.randomUUID().toString()

    fun getLoginCredentials(): LoginCredentials? = credentialStore.loginCredentials

    fun setLoginCredentials(loginCredentials: LoginCredentials?) {
        credentialStore.loginCredentials = loginCredentials
    }

    fun getAuthenticatedCredentials(): AuthenticatedCredentials? = credentialStore.authenticatedCredentials

    fun setAddress(address: String) {
        credentialStore.address = address
    }

    fun getAddress(): String? = credentialStore.address

    suspend fun authenticate(
        address: String,
        loginCredentials: LoginCredentials
    ): Result<AuthenticatedCredentials> {
        Timber.d("authenticate(address: $address)")
        val authenticationResult =
            userService.authenticate(
                url = address,
                username = loginCredentials.username,
                password = loginCredentials.password,
                deviceId = deviceId
            )

        return when (authenticationResult) {
            is NetworkResult.Success<AuthenticationResult> -> {
                val authenticatedCredentials = AuthenticatedCredentials(
                    accessToken = authenticationResult.body.accessToken,
                    userId = authenticationResult.body.user.id,
                    canDownload = authenticationResult.body.user.policy?.enableContentDownloading ?: false
                )
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

    fun buildEmbyPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials
    ): String? {
        if (credentialStore.address == null) {
            Timber.w("Invalid emby address")
            return null
        }

        return "${credentialStore.address}/emby" +
            "/Audio/$itemId" +
            "/universal" +
            "?UserId=${authenticatedCredentials.userId}" +
            "&DeviceId=$deviceId" +
            "&PlaySessionId=${UUID.randomUUID()}" +
            "&Container=opus,mp3|mp3,aac,m4a,m4b|aac,flac,webma,webm,wav,ogg" +
            "&TranscodingContainer=ts" +
            "&TranscodingProtocol=hls" +
            "&MaxSampleRate=48000" +
            "&EnableRedirection=true" +
            "&EnableRemoteMedia=true" +
            "&AudioCodec=aac" +
            "&api_key=${authenticatedCredentials.accessToken}"
    }

    /**
     * The URL for the original, non-transcoded file, for offline download. Prefers
     * `/Items/{id}/Download`, which needs the user's `EnableContentDownloading` permission
     * (captured at sign-in); falls back to the static (untranscoded) `/Audio/{id}/stream` for
     * users without it.
     */
    fun buildDownloadPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials
    ): String? {
        if (credentialStore.address == null) {
            Timber.w("Invalid emby address")
            return null
        }

        return if (authenticatedCredentials.canDownload) {
            "${credentialStore.address}/emby/Items/$itemId/Download?api_key=${authenticatedCredentials.accessToken}"
        } else {
            "${credentialStore.address}/emby/Audio/$itemId/stream?static=true&api_key=${authenticatedCredentials.accessToken}"
        }
    }
}
