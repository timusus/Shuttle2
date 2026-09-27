package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.checkSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.PLEX_TOKEN
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.provider.plex.http.plexClientHeaders
import com.simplecityapps.shuttle.model.Song
import io.ktor.http.HttpStatusCode
import java.net.URLEncoder
import java.util.UUID
import timber.log.Timber

class PlexAuthenticationManager(
    private val userService: UserService,
    private val credentialStore: ServerCredentialStore,
    private val clientIdentity: ClientIdentity
) {
    fun getLoginCredentials(): LoginCredentials? = credentialStore.loginCredentials

    fun setLoginCredentials(loginCredentials: LoginCredentials?) {
        credentialStore.loginCredentials = loginCredentials
    }

    fun getAuthenticatedCredentials(): AuthenticatedCredentials? = credentialStore.authenticatedCredentials

    fun setAddress(address: String) {
        credentialStore.address = address
    }

    fun getAddress(): String? = credentialStore.address

    /** [result], after signing out when the server rejected [credentials] with a 401 (#577). */
    fun <T : Any> checkSession(
        credentials: AuthenticatedCredentials,
        result: NetworkResult<T>
    ): NetworkResult<T> = credentialStore.checkSession(credentials, result)

    suspend fun authenticate(
        address: String,
        loginCredentials: LoginCredentials
    ): Result<AuthenticatedCredentials> {
        Timber.d("authenticate(address: $address)")
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

    fun buildPlexPath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials
    ): String? {
        if (credentialStore.address == null) {
            Timber.w("Invalid plex address (${credentialStore.address})")
            return null
        }

        return "${credentialStore.address}${song.externalId}" +
            "?X-Plex-Token=${authenticatedCredentials.accessToken}" +
            "&X-Plex-Client-Identifier=${clientIdentity.id}" +
            "&X-Plex-Device=Android"
    }

    /**
     * An HLS stream of [song] transcoded to AAC at up to [maxBitrateKbps], from Plex's universal transcoder. HLS keeps
     * the transcode seekable. The client identity goes in the query, since the player's requests don't carry the
     * `X-Plex-*` headers; the profile extra asks for AAC in MPEG-TS whatever profile the server picks for the client.
     * Null when the address or the song's ratingKey is missing.
     */
    fun buildPlexTranscodePath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials,
        maxBitrateKbps: Int,
        session: String = UUID.randomUUID().toString()
    ): String? {
        val address = credentialStore.address ?: run {
            Timber.w("Invalid plex address (null)")
            return null
        }
        val ratingKey = plexRatingKey(song.path) ?: run {
            Timber.w("No plex ratingKey in ${song.path}")
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
            query.entries.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
    }

    /**
     * A single-file MP3 transcode of [song] at [bitrateKbps], from Plex's universal transcoder, for downloading a
     * format the player can't decode. `protocol=http` (rather than [buildPlexTranscodePath]'s `hls`) asks for one
     * continuous file instead of a manifest and segments, since Media3's downloader saves whatever's at the URL as
     * a single item (#567). Null when the address or the song's ratingKey is missing.
     */
    fun buildPlexProgressiveTranscodePath(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials,
        bitrateKbps: Int
    ): String? {
        val address = credentialStore.address ?: run {
            Timber.w("Invalid plex address (null)")
            return null
        }
        val ratingKey = plexRatingKey(song.path) ?: run {
            Timber.w("No plex ratingKey in ${song.path}")
            return null
        }
        val query = linkedMapOf(
            "path" to "$METADATA_PATH$ratingKey",
            "protocol" to "http",
            "directPlay" to "0",
            "directStream" to "0",
            "musicBitrate" to bitrateKbps.toString(),
            "X-Plex-Client-Profile-Extra" to "add-transcode-target(type=musicProfile&context=static&protocol=http&container=mp3&audioCodec=mp3)"
        ) + plexClientHeaders(clientIdentity) + (PLEX_TOKEN to authenticatedCredentials.accessToken)

        return "$address/music/:/transcode/universal/start.mp3?" +
            query.entries.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
    }
}
