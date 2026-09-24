package com.simplecityapps.provider.emby

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.AuthenticationResult
import com.simplecityapps.provider.emby.http.LoginCredentials
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.provider.emby.http.authenticate
import com.simplecityapps.provider.emby.http.me
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

    /**
     * Re-fetches `Policy.EnableContentDownloading` for [authenticatedCredentials] and updates the
     * stored credentials, so a permission change on the server (#322) takes effect without a fresh
     * sign-in. Called wherever the session is already being validated with the server, rather than
     * on a dedicated poll. Keeps the cached value on failure.
     */
    suspend fun refreshDownloadPermission(
        address: String,
        authenticatedCredentials: AuthenticatedCredentials
    ): AuthenticatedCredentials = when (val result = userService.me(address, authenticatedCredentials.accessToken)) {
        is NetworkResult.Success -> {
            val refreshed = authenticatedCredentials.copy(canDownload = result.body.policy?.enableContentDownloading ?: false)
            credentialStore.authenticatedCredentials = refreshed
            refreshed
        }

        is NetworkResult.Failure -> {
            Timber.w(result.error, "Failed to refresh the download permission")
            authenticatedCredentials
        }
    }

    /** Persists that the current user can't use the Download endpoint, so later downloads go straight to the static stream. */
    fun disableDownloadPermission() {
        credentialStore.authenticatedCredentials = credentialStore.authenticatedCredentials?.copy(canDownload = false)
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
     * (refreshed by [refreshDownloadPermission]); falls back to the static (untranscoded)
     * [buildStreamPath] for users without it.
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
            buildStreamPath(itemId, authenticatedCredentials)
        }
    }

    /**
     * The static (untranscoded) stream URL: used directly by [buildDownloadPath] when download
     * permission is off, and as the fallback when the Download endpoint rejects a download with
     * 401/403 because permission changed on the server after it was cached.
     */
    fun buildStreamPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials
    ): String? {
        if (credentialStore.address == null) {
            Timber.w("Invalid emby address")
            return null
        }

        return "${credentialStore.address}/emby/Audio/$itemId/stream?static=true&api_key=${authenticatedCredentials.accessToken}"
    }
}
