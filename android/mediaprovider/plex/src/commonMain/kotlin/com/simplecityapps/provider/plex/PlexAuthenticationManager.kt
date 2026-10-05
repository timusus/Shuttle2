package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.checkSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.PLEX_PLATFORM
import com.simplecityapps.provider.plex.http.PLEX_TOKEN
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.provider.plex.http.formUrlEncode
import com.simplecityapps.provider.plex.http.plexClientHeaders
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Song

class PlexAuthenticationManager(
    private val userService: UserService,
    val credentialStore: ServerCredentialStore,
    private val clientIdentity: ClientIdentity
) {
    private val logger = Logger.tagged("PlexAuthenticationManager")

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
        val authenticationResult =
            userService.authenticate(
                username = loginCredentials.username,
                password = loginCredentials.password,
                authCode = loginCredentials.authCode
            )

        return when (authenticationResult) {
            is NetworkResult.Success<AuthenticationResult> -> {
                val authenticatedCredentials = AuthenticatedCredentials(authenticationResult.body.user.authToken, authenticationResult.body.user.id)
                credentialStore.authenticatedCredentials = authenticatedCredentials
                Result.success(authenticatedCredentials)
            }

            // Leaves the stored session alone: a mistyped password in the sign-in dialog mustn't sign out a working
            // session (#596). A session the server rejects is cleared by checkSession, when it's used.
            is NetworkResult.Failure -> Result.failure(authenticationResult.error)
        }
    }

    fun buildPlexPath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials
    ): String? {
        if (credentialStore.address == null) {
            logger.warn { "Invalid plex address (${credentialStore.address})" }
            return null
        }

        return "${credentialStore.address}${song.externalId}" +
            "?X-Plex-Token=${authenticatedCredentials.accessToken}" +
            "&X-Plex-Client-Identifier=${clientIdentity.id}" +
            "&X-Plex-Device=$PLEX_PLATFORM"
    }

    /**
     * An HLS stream of [song] transcoded to AAC at up to [maxBitrateKbps], from Plex's universal transcoder. HLS keeps
     * the transcode seekable. The client identity goes in the query, since the player's requests don't carry the
     * `X-Plex-*` headers; the profile extra asks for AAC in MPEG-TS whatever profile the server picks for the client.
     * [session] names the transcode on the server and is its `X-Plex-Session-Identifier`. Null when the address or the
     * song's ratingKey is missing.
     */
    fun buildPlexTranscodePath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials,
        maxBitrateKbps: Int,
        session: String
    ): String? {
        val address = credentialStore.address ?: run {
            logger.warn { "Invalid plex address (null)" }
            return null
        }
        val ratingKey = plexRatingKey(song.path) ?: run {
            logger.warn { "No plex ratingKey in ${song.path}" }
            return null
        }
        val query = linkedMapOf(
            "path" to "$METADATA_PATH$ratingKey",
            "protocol" to "hls",
            "directPlay" to "0",
            "directStream" to "0",
            "musicBitrate" to maxBitrateKbps.toString(),
            "session" to session,
            "X-Plex-Session-Identifier" to session,
            "X-Plex-Client-Profile-Extra" to "add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=aac)"
        ) + plexClientHeaders(clientIdentity) + (PLEX_TOKEN to authenticatedCredentials.accessToken)

        return "$address/music/:/transcode/universal/start.m3u8?" +
            query.entries.joinToString("&") { (name, value) -> "$name=${formUrlEncode(value)}" }
    }

    /**
     * A single-file MP3 transcode of [song] at [bitrateKbps], from Plex's universal transcoder, for downloading a
     * format the player can't decode. `protocol=http` (rather than [buildPlexTranscodePath]'s `hls`) asks for one
     * continuous file instead of a manifest and segments, since Media3's downloader saves whatever's at the URL as
     * a single item (#567). It carries [sessionIdentifier], the song's streams' `X-Plex-Session-Identifier`, on a
     * session of its own: Plex answers 400 to a transcode of a track under an identifier other than the last one it saw
     * for it (#888). Null when the address or the song's ratingKey is missing.
     */
    fun buildPlexProgressiveTranscodePath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials,
        bitrateKbps: Int,
        sessionIdentifier: String
    ): String? = progressiveTranscodePath(
        song = song,
        authenticatedCredentials = authenticatedCredentials,
        bitrateKbps = bitrateKbps,
        context = "static",
        extra = mapOf("session" to "$sessionIdentifier-download", "X-Plex-Session-Identifier" to sessionIdentifier)
    )

    /**
     * A single-file MP3 stream of [song] transcoded at up to [maxBitrateKbps], starting [startPositionMs] into it, for a
     * player with no HLS (iOS's engine). A progressive transcode has no length to seek in, so a seek opens a new one at
     * the position (Plex's `offset`, in seconds). [session] names the transcode on the server, so a re-open at another
     * position replaces it, and [sessionIdentifier] is its `X-Plex-Session-Identifier`. Null when the address or the
     * song's ratingKey is missing.
     */
    fun buildPlexProgressiveStreamPath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials,
        maxBitrateKbps: Int,
        startPositionMs: Long = 0,
        session: String,
        sessionIdentifier: String
    ): String? = progressiveTranscodePath(
        song = song,
        authenticatedCredentials = authenticatedCredentials,
        bitrateKbps = maxBitrateKbps,
        context = "streaming",
        extra = buildMap {
            if (startPositionMs > 0) put("offset", offsetSeconds(startPositionMs))
            put("session", session)
            put("X-Plex-Session-Identifier", sessionIdentifier)
        }
    )

    private fun progressiveTranscodePath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials,
        bitrateKbps: Int,
        context: String,
        extra: Map<String, String> = emptyMap()
    ): String? {
        val address = credentialStore.address ?: run {
            logger.warn { "Invalid plex address (null)" }
            return null
        }
        val ratingKey = plexRatingKey(song.path) ?: run {
            logger.warn { "No plex ratingKey in ${song.path}" }
            return null
        }
        val query = linkedMapOf(
            "path" to "$METADATA_PATH$ratingKey",
            "protocol" to "http",
            "directPlay" to "0",
            "directStream" to "0",
            "musicBitrate" to bitrateKbps.toString()
        ) + extra + mapOf(
            "X-Plex-Client-Profile-Extra" to "add-transcode-target(type=musicProfile&context=$context&protocol=http&container=mp3&audioCodec=mp3)"
        ) + plexClientHeaders(clientIdentity) + (PLEX_TOKEN to authenticatedCredentials.accessToken)

        return "$address/music/:/transcode/universal/start.mp3?" +
            query.entries.joinToString("&") { (name, value) -> "$name=${formUrlEncode(value)}" }
    }

    /** [positionMs] as the seconds Plex's `offset` takes, to the millisecond. */
    private fun offsetSeconds(positionMs: Long): String = "${positionMs / 1000}.${(positionMs % 1000).toString().padStart(3, '0')}"
}
