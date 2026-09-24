package com.simplecityapps.provider.jellyfin

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.AuthenticationResult
import com.simplecityapps.provider.jellyfin.http.LoginCredentials
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.provider.jellyfin.http.authenticate
import com.simplecityapps.provider.jellyfin.http.mediaBrowserAuthorization
import java.util.UUID
import timber.log.Timber

class JellyfinAuthenticationManager(
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

    /** The `Authorization` header value for requests made with [authenticatedCredentials]. */
    fun authorizationHeader(authenticatedCredentials: AuthenticatedCredentials): String = mediaBrowserAuthorization(deviceId, authenticatedCredentials.accessToken)

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

    fun buildJellyfinPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials
    ): String? {
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
            "&ApiKey=${authenticatedCredentials.accessToken}"
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
            Timber.w("Invalid jellyfin address (${credentialStore.address})")
            return null
        }

        return if (authenticatedCredentials.canDownload) {
            "${credentialStore.address}/Items/$itemId/Download?ApiKey=${authenticatedCredentials.accessToken}"
        } else {
            "${credentialStore.address}/Audio/$itemId/stream?static=true&ApiKey=${authenticatedCredentials.accessToken}"
        }
    }
}
