package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.TranscodeTarget
import com.simplecityapps.mediaprovider.server.checkSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.settings.TranscodeFormat
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

/** A [server]'s sign-in, its stored session, and the URLs the player and downloads open with that session's token. */
open class MediaBrowserAuthenticationManager(
    val server: MediaBrowserServer,
    private val userService: UserService,
    val credentialStore: ServerCredentialStore,
    protected val clientIdentity: ClientIdentity,
    private val streamProfile: StreamProfile
) {
    private val logger = Logger.tagged("${server.name}AuthenticationManager")

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

    suspend fun authenticate(
        address: String,
        loginCredentials: LoginCredentials
    ): Result<AuthenticatedCredentials> {
        logger.debug { "authenticate(address: $address)" }
        return when (val authenticationResult = userService.authenticate(address, loginCredentials.username, loginCredentials.password)) {
            is NetworkResult.Success<AuthenticationResult> -> Result.success(store(authenticationResult.body))

            // Leaves the stored session alone: a mistyped password in the sign-in dialog mustn't sign out a working
            // session (#596). A session the server rejects is cleared by checkSession, when it's used.
            is NetworkResult.Failure -> Result.failure(authenticationResult.error)
        }
    }

    /** Stores the session [result] signed in to, and returns it. */
    protected fun store(result: AuthenticationResult): AuthenticatedCredentials {
        val authenticatedCredentials = AuthenticatedCredentials(
            accessToken = result.accessToken,
            userId = result.user.id,
            canDownload = result.user.policy?.enableContentDownloading ?: false
        )
        credentialStore.authenticatedCredentials = authenticatedCredentials
        return authenticatedCredentials
    }

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
    ): AuthenticatedCredentials? = when (val result = userService.me(address, authenticatedCredentials.accessToken)) {
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
     * with a [maxBitrateKbps] cap, when its bitrate is under the cap; otherwise it transcodes to [format] (Auto being
     * the profile's own: AAC over HLS on Android, which stays seekable). A null cap streams the original, whatever its
     * bitrate. [startPositionMs] starts a transcode that far in (`StartTimeTicks`): a progressive transcode can't be
     * range-seeked, so a seek restarts it there. The server ignores it for direct play, which seeks by range.
     * The stream's `PlaySessionId` is [playId], the play whose reports carry it, else a new one.
     */
    fun buildUniversalPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials,
        maxBitrateKbps: Int?,
        startPositionMs: Long = 0,
        format: TranscodeFormat = TranscodeFormat.Auto,
        playId: String? = null
    ): String? = universalPath(
        itemId = itemId,
        authenticatedCredentials = authenticatedCredentials,
        directPlayContainers = streamProfile.directPlayContainers,
        target = streamProfile.streamTarget(format),
        maxBitrateKbps = maxBitrateKbps,
        startPositionMs = startPositionMs,
        playSessionId = playId ?: Uuid.random().toString()
    )

    /**
     * A download transcoded to one progressive file in [format] at up to [maxBitrateKbps], and its MIME type. The
     * server sends the original only when it's already that codec and within the cap, so the file is the type
     * recorded either way.
     */
    fun buildTranscodedDownloadPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials,
        maxBitrateKbps: Int?,
        format: TranscodeFormat
    ): DownloadSource? {
        val target = streamProfile.downloadTarget(format)
        val path = universalPath(
            itemId = itemId,
            authenticatedCredentials = authenticatedCredentials,
            directPlayContainers = "${target.container}|${target.codec.codec}",
            target = target,
            maxBitrateKbps = maxBitrateKbps
        ) ?: return null
        return DownloadSource(path, target.mimeType)
    }

    /** What the [StreamProfile] transcodes a stream to in [format]. */
    fun streamTarget(format: TranscodeFormat): TranscodeTarget = streamProfile.streamTarget(format)

    /**
     * Whether the player decodes a file in [audioCodec] as it is, so the server direct-plays it within the cap. A song
     * carries no container, so only the codec decides; an unknown one is taken to play.
     */
    fun directPlays(audioCodec: String?): Boolean = streamProfile.directPlayFormats.isDecodable(container = null, audioCodec = audioCodec)

    private fun universalPath(
        itemId: String,
        authenticatedCredentials: AuthenticatedCredentials,
        directPlayContainers: String,
        target: TranscodeTarget,
        maxBitrateKbps: Int?,
        startPositionMs: Long = 0,
        playSessionId: String = Uuid.random().toString()
    ): String? {
        val apiAddress = apiAddress() ?: return null
        return apiAddress +
            "/Audio/$itemId" +
            "/universal" +
            "?UserId=${authenticatedCredentials.userId}" +
            "&DeviceId=${clientIdentity.id}" +
            "&PlaySessionId=$playSessionId" +
            "&Container=$directPlayContainers" +
            "&TranscodingContainer=${target.container}" +
            "&TranscodingProtocol=${target.protocol}" +
            "&EnableRedirection=true" +
            "&EnableRemoteMedia=true" +
            "&AudioCodec=${target.codec.codec}" +
            maxBitrateKbps?.let { kbps -> "&MaxStreamingBitrate=${kbps * 1000}" }.orEmpty() +
            startPositionMs.takeIf { it > 0 }?.let { ms -> "&StartTimeTicks=${StreamProfile.startTimeTicks(ms)}" }.orEmpty() +
            "&${server.tokenParameter}=${authenticatedCredentials.accessToken}"
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
        val apiAddress = apiAddress() ?: return null
        return if (authenticatedCredentials.canDownload) {
            "$apiAddress/Items/$itemId/Download?${server.tokenParameter}=${authenticatedCredentials.accessToken}"
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
        val apiAddress = apiAddress() ?: return null
        return "$apiAddress/Audio/$itemId/stream?static=true&${server.tokenParameter}=${authenticatedCredentials.accessToken}"
    }

    /** The address the stream and download routes sit under, or null when no server address is set. */
    private fun apiAddress(): String? {
        val address = credentialStore.address
        if (address == null) {
            logger.warn { "Invalid ${server.name.lowercase()} address" }
            return null
        }
        return "$address${server.apiPrefix}"
    }
}
