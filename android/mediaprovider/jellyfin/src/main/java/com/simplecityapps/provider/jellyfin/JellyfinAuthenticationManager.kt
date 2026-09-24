package com.simplecityapps.provider.jellyfin

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.HttpStatusCode
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.AuthenticationResult
import com.simplecityapps.provider.jellyfin.http.LoginCredentials
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.provider.jellyfin.http.authenticate
import com.simplecityapps.provider.jellyfin.http.me
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

    /**
     * Re-fetches `Policy.EnableContentDownloading` for [authenticatedCredentials] and updates the
     * stored credentials, so a permission change on the server (#322) takes effect without a fresh
     * sign-in. Called wherever the session is already being validated with the server, rather than
     * on a dedicated poll. Keeps the cached value on failure.
     */
    suspend fun refreshDownloadPermission(
        address: String,
        authenticatedCredentials: AuthenticatedCredentials
    ): AuthenticatedCredentials = when (val result = userService.me(address, authorizationHeader(authenticatedCredentials))) {
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
     * (refreshed by [refreshDownloadPermission]); falls back to the static (untranscoded)
     * [buildStreamPath] for users without it.
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
            Timber.w("Invalid jellyfin address (${credentialStore.address})")
            return null
        }

        return "${credentialStore.address}/Audio/$itemId/stream?static=true&ApiKey=${authenticatedCredentials.accessToken}"
    }
}
