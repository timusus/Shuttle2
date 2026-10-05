package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.ServerConnection
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.SignInPin
import com.simplecityapps.mediaprovider.server.TranscodeCodec
import com.simplecityapps.mediaprovider.server.checkSession
import com.simplecityapps.networking.retrofit.NetworkResult
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

    /** A new plex.tv sign-in PIN, with the web sign-in that approves it on this device. */
    suspend fun createPin(): Result<SignInPin> = userService.createPin().toResult().map { pin ->
        SignInPin(
            id = pin.id,
            code = pin.code,
            authUrl = authUrl(pin.code),
            linkUrl = LINK_URL,
            expiresInSeconds = pin.expiresIn ?: DEFAULT_PIN_EXPIRY_SECONDS
        )
    }

    /** The account's token once the user has approved [pin]; null while it's still waiting. */
    suspend fun checkPin(pin: SignInPin): Result<String?> = userService.pin(pin.id, pin.code).toResult().map { it.authToken?.takeIf(String::isNotEmpty) }

    /** The Plex Media Servers on the account signed in with [accountToken], owned or shared, that have a token and an address. */
    suspend fun servers(accountToken: String): Result<List<AccountServer>> = userService.resources(accountToken).toResult().map { resources ->
        resources.filter { it.isServer }.mapNotNull { resource ->
            val token = resource.accessToken?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            AccountServer(
                id = resource.clientIdentifier,
                name = resource.name,
                owned = resource.owned,
                accessToken = token,
                connections = resource.connections.map { ServerConnection(it.uri, it.local, it.relay, it.address, it.port) }
            ).takeIf { it.connections.isNotEmpty() }
        }
    }

    /** Whether [server] is the one signed in now: its id is the saved session's, or one of its connections is the saved address. */
    fun isSignedInTo(server: AccountServer): Boolean {
        if (credentialStore.authenticatedCredentials?.userId == server.id) return true
        val address = credentialStore.address ?: return false
        return server.connections.any { connection -> connection.matches(address) }
    }

    /**
     * Saves the first of [server]'s connections to answer, in [orderConnections]' order, as the address, and the server's
     * own token as the session; a shared server rejects the account's. The session's user id is the server's id, so a
     * later sign-in can tell it's the same server whichever of its connections answers. Any password saved by the old
     * plex.tv sign-in is forgotten. Fails, leaving the saved server alone, when none of them answers.
     */
    suspend fun connect(server: AccountServer): Result<Unit> {
        logger.debug { "connect(server: ${server.name})" }
        val uri = firstReachable(server.connections) { uri -> userService.isReachable(uri) }
            ?: return Result.failure(Exception("Couldn't reach ${server.name}. Check it's running, then try again."))
        credentialStore.address = uri
        credentialStore.loginCredentials = null
        credentialStore.authenticatedCredentials = AuthenticatedCredentials(server.accessToken, server.id)
        return Result.success(Unit)
    }

    /** Plex's web sign-in, which approves [code] for this client once the user signs in. */
    private fun authUrl(code: String): String = "$AUTH_URL#?" +
        listOf(
            "clientID" to clientIdentity.id,
            "code" to code,
            "context[device][product]" to clientIdentity.clientName
        ).joinToString("&") { (name, value) -> "${formUrlEncode(name)}=${formUrlEncode(value)}" }

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
     * An HLS stream of [song] transcoded to [codec] (AAC or MP3) at up to [maxBitrateKbps], from Plex's universal
     * transcoder. HLS keeps the transcode seekable. The client identity goes in the query, since the player's requests
     * don't carry the `X-Plex-*` headers; the profile extra asks for [codec] in MPEG-TS whatever profile the server picks
     * for the client.
     * [session] names the transcode on the server and is its `X-Plex-Session-Identifier`. Null when the address or the
     * song's ratingKey is missing.
     */
    fun buildPlexTranscodePath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials,
        maxBitrateKbps: Int,
        session: String,
        codec: TranscodeCodec = TranscodeCodec.Aac
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
            "X-Plex-Client-Profile-Extra" to "add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=${codec.codec})"
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

    private fun <T : Any> NetworkResult<T>.toResult(): Result<T> = when (this) {
        is NetworkResult.Success -> Result.success(body)
        is NetworkResult.Failure -> Result.failure(error)
    }

    private companion object {
        const val AUTH_URL = "https://app.plex.tv/auth"
        const val LINK_URL = "https://plex.tv/link"
        const val DEFAULT_PIN_EXPIRY_SECONDS = 900
    }
}
