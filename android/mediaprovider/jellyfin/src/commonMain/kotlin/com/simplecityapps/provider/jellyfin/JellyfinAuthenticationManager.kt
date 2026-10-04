package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.QuickConnectCode
import com.simplecityapps.mediaprovider.server.QuickConnectPollState
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.checkSession
import com.simplecityapps.mediaprovider.server.mediaBrowserAuthorization
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.jellyfin.http.AuthenticationResult
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.logging.Logger
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

class JellyfinAuthenticationManager(
    private val userService: UserService,
    val credentialStore: ServerCredentialStore,
    private val clientIdentity: ClientIdentity,
    private val streamProfile: StreamProfile
) {
    private val logger = Logger.tagged("JellyfinAuthenticationManager")

    fun getLoginCredentials(): LoginCredentials? = credentialStore.loginCredentials

    fun setLoginCredentials(loginCredentials: LoginCredentials?) {
        credentialStore.loginCredentials = loginCredentials
    }

    fun getAuthenticatedCredentials(): AuthenticatedCredentials? = credentialStore.authenticatedCredentials

    fun setAddress(address: String) {
        credentialStore.address = address
    }

    fun getAddress(): String? = credentialStore.address

    /** Forgets this server's address, saved login and session. */
    fun forgetServer() = credentialStore.clear()

    /** [result], after signing out when the server rejected [credentials] with a 401 (#577). */
    fun <T : Any> checkSession(
        credentials: AuthenticatedCredentials,
        result: NetworkResult<T>
    ): NetworkResult<T> = credentialStore.checkSession(credentials, result)

    /** The `Authorization` header value for requests made with [authenticatedCredentials]. */
    fun authorizationHeader(authenticatedCredentials: AuthenticatedCredentials): String = mediaBrowserAuthorization(
        deviceId = clientIdentity.id,
        token = authenticatedCredentials.accessToken,
        deviceName = clientIdentity.deviceName,
        version = clientIdentity.version
    )

    suspend fun authenticate(
        address: String,
        loginCredentials: LoginCredentials
    ): Result<AuthenticatedCredentials> {
        logger.debug { "authenticate(address: $address)" }
        val authenticationResult =
            userService.authenticate(
                url = address,
                username = loginCredentials.username,
                password = loginCredentials.password,
                deviceId = clientIdentity.id,
                deviceName = clientIdentity.deviceName,
                version = clientIdentity.version
            )

        return when (authenticationResult) {
            is NetworkResult.Success<AuthenticationResult> -> {
                val authenticatedCredentials = authenticationResult.body.toAuthenticatedCredentials()
                credentialStore.authenticatedCredentials = authenticatedCredentials
                Result.success(authenticatedCredentials)
            }

            // Leaves the stored session alone: a mistyped password in the sign-in dialog mustn't sign out a working
            // session (#596). A session the server rejects is cleared by checkSession, when it's used.
            is NetworkResult.Failure -> Result.failure(authenticationResult.error)
        }
    }

    /** Whether the server supports the Quick Connect sign-in flow. Servers before it existed return false, never an error. */
    suspend fun isQuickConnectEnabled(address: String): Boolean = when (val result = userService.isQuickConnectEnabled(address)) {
        is NetworkResult.Success -> result.body
        is NetworkResult.Failure -> false
    }

    /** Starts a Quick Connect attempt, returning the code to show the user and the secret used to poll and redeem it. */
    suspend fun initiateQuickConnect(address: String): Result<QuickConnectCode> = when (
        val result = userService.initiateQuickConnect(address, clientIdentity.id, clientIdentity.deviceName, clientIdentity.version)
    ) {
        is NetworkResult.Success -> Result.success(QuickConnectCode(code = result.body.code, secret = result.body.secret))
        is NetworkResult.Failure -> Result.failure(result.error)
    }

    /** A 401 means the code was denied or expired server-side; Jellyfin's API has no separate signal for that. */
    suspend fun pollQuickConnect(address: String, secret: String): Result<QuickConnectPollState> = when (
        val result = userService.pollQuickConnect(address, secret, clientIdentity.id, clientIdentity.deviceName, clientIdentity.version)
    ) {
        is NetworkResult.Success -> Result.success(if (result.body.authenticated) QuickConnectPollState.Authenticated else QuickConnectPollState.Pending)

        is NetworkResult.Failure ->
            if ((result.error as? RemoteServiceHttpError)?.httpStatusCode == HttpStatusCode.Unauthorized) {
                Result.success(QuickConnectPollState.Denied)
            } else {
                Result.failure(result.error)
            }
    }

    /** Redeems an approved Quick Connect secret for a token, stored the same way a password sign-in's token is. */
    suspend fun authenticateWithQuickConnect(address: String, secret: String): Result<AuthenticatedCredentials> = when (
        val result = userService.authenticateWithQuickConnect(address, secret, clientIdentity.id, clientIdentity.deviceName, clientIdentity.version)
    ) {
        is NetworkResult.Success<AuthenticationResult> -> {
            val authenticatedCredentials = result.body.toAuthenticatedCredentials()
            credentialStore.authenticatedCredentials = authenticatedCredentials
            Result.success(authenticatedCredentials)
        }

        is NetworkResult.Failure -> Result.failure(result.error)
    }

    private fun AuthenticationResult.toAuthenticatedCredentials() = AuthenticatedCredentials(
        accessToken = accessToken,
        userId = user.id,
        canDownload = user.policy?.enableContentDownloading ?: false
    )

    /**
     * Re-fetches `Policy.EnableContentDownloading` for [authenticatedCredentials] and updates the
     * stored credentials, so a permission change on the server (#322) takes effect without a fresh
     * sign-in. Called wherever the session is already being validated with the server, rather than
     * on a dedicated poll. Keeps the cached value on failure, but returns null when the server rejects the session
     * (a 401, #577): that signs out, so the sync signs in again with the saved login.
     */
    suspend fun refreshDownloadPermission(
        address: String,
        authenticatedCredentials: AuthenticatedCredentials
    ): AuthenticatedCredentials? = when (val result = userService.me(address, authorizationHeader(authenticatedCredentials))) {
        is NetworkResult.Success -> {
            val refreshed = authenticatedCredentials.copy(canDownload = result.body.policy?.enableContentDownloading ?: false)
            // Saved only while it's still the stored session, so this never brings back a session that was cleared,
            // or overwrites a newer sign-in, while the request was in flight (#596)
            credentialStore.compareAndSetAuthenticatedCredentials(expected = authenticatedCredentials, new = refreshed)
            refreshed
        }

        is NetworkResult.Failure -> {
            if ((result.error as? RemoteServiceHttpError)?.httpStatusCode == HttpStatusCode.Unauthorized) {
                credentialStore.expireSession(authenticatedCredentials)
                null
            } else {
                logger.warn(result.error) { "Failed to refresh the download permission" }
                authenticatedCredentials
            }
        }
    }

    /** Persists that the current user can't use the Download endpoint, so later downloads go straight to the static stream. */
    fun disableDownloadPermission() {
        val current = credentialStore.authenticatedCredentials ?: return
        credentialStore.compareAndSetAuthenticatedCredentials(expected = current, new = current.copy(canDownload = false))
    }

    /**
     * The universal stream URL. The server direct-plays the original when it's a format the [StreamProfile] lists and,
     * with a [maxBitrateKbps] cap, when its bitrate is under the cap; otherwise it transcodes to the profile's target
     * (AAC over HLS on Android, which stays seekable). A null cap streams the original, whatever its bitrate.
     * [startPositionMs] starts a transcode that far in (`StartTimeTicks`): a progressive transcode can't be
     * range-seeked, so a seek restarts it there. The server ignores it for direct play, which seeks by range.
     */
    fun buildJellyfinPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials,
        maxBitrateKbps: Int?,
        startPositionMs: Long = 0
    ): String? {
        if (credentialStore.address == null) {
            logger.warn { "Invalid jellyfin address (${credentialStore.address})" }
            return null
        }

        return "${credentialStore.address}" +
            "/Audio/$itemId" +
            "/universal" +
            "?UserId=${authenticatedCredentials.userId}" +
            "&DeviceId=${clientIdentity.id}" +
            "&PlaySessionId=${Uuid.random()}" +
            "&Container=${streamProfile.directPlayContainers}" +
            "&TranscodingContainer=${streamProfile.transcodingContainer}" +
            "&TranscodingProtocol=${streamProfile.transcodingProtocol}" +
            "&EnableRedirection=true" +
            "&EnableRemoteMedia=true" +
            "&AudioCodec=${streamProfile.transcodingAudioCodec}" +
            maxBitrateKbps?.let { kbps -> "&MaxStreamingBitrate=${kbps * 1000}" }.orEmpty() +
            startPositionMs.takeIf { it > 0 }?.let { ms -> "&StartTimeTicks=${StreamProfile.startTimeTicks(ms)}" }.orEmpty() +
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
            logger.warn { "Invalid jellyfin address (${credentialStore.address})" }
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
            logger.warn { "Invalid jellyfin address (${credentialStore.address})" }
            return null
        }

        return "${credentialStore.address}/Audio/$itemId/stream?static=true&ApiKey=${authenticatedCredentials.accessToken}"
    }
}
