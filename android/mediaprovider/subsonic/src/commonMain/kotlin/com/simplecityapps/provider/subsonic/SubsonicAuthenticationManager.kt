package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.checkSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.subsonic.http.SubsonicError
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager

/** What a server said about itself when it was signed in to. */
data class SubsonicServerInfo(
    val openSubsonic: Boolean,
    /** The server software (`navidrome`), on an OpenSubsonic server. */
    val type: String?,
    val serverVersion: String?,
    /** The OpenSubsonic extensions it supports (`transcoding`, `apiKeyAuthentication`, ...). */
    val extensions: Set<String>
) {
    fun supports(extension: String): Boolean = extension in extensions

    companion object {
        const val TRANSCODING = "transcoding"
        const val API_KEY_AUTHENTICATION = "apiKeyAuthentication"
        const val TRANSCODE_OFFSET = "transcodeOffset"
    }
}

/**
 * Signs in to a Subsonic server and signs its requests. Subsonic has no session: every request carries the credentials,
 * so the stored [AuthenticatedCredentials] are the credentials themselves, a username ([AuthenticatedCredentials.userId])
 * and password ([AuthenticatedCredentials.accessToken]), or with no username an API key. They live in the
 * [credentialStore] like any server's, which signs out when the server rejects them (error 40 or 44, as a 401).
 */
class SubsonicAuthenticationManager(
    private val service: SubsonicService,
    val credentialStore: ServerCredentialStore,
    private val preferences: SecurePreferenceManager
) {
    private val logger = Logger.tagged("SubsonicAuthenticationManager")

    fun getLoginCredentials(): LoginCredentials? = credentialStore.loginCredentials

    fun setLoginCredentials(loginCredentials: LoginCredentials?) {
        credentialStore.loginCredentials = loginCredentials
    }

    fun getAuthenticatedCredentials(): AuthenticatedCredentials? = credentialStore.authenticatedCredentials

    fun getAddress(): String? = credentialStore.address

    fun setAddress(address: String) {
        credentialStore.address = address
    }

    /** Forgets this server's address, saved login, credentials and what it said about itself. */
    fun forgetServer() {
        credentialStore.clear()
        serverInfo = null
        sendPassword = false
    }

    /** What the signed-in server supports; null before a sign-in. */
    var serverInfo: SubsonicServerInfo?
        get() {
            val version = preferences.getString(SERVER_VERSION_KEY) ?: return null
            return SubsonicServerInfo(
                openSubsonic = preferences.getBoolean(OPEN_SUBSONIC_KEY),
                type = preferences.getString(SERVER_TYPE_KEY),
                serverVersion = version.ifEmpty { null },
                extensions = preferences.getString(EXTENSIONS_KEY)?.split(',')?.filter(String::isNotEmpty)?.toSet().orEmpty()
            )
        }
        private set(value) {
            preferences.putBoolean(OPEN_SUBSONIC_KEY, value?.openSubsonic ?: false)
            preferences.putString(SERVER_TYPE_KEY, value?.type)
            // Written even when the server sent none, as the marker that a sign-in recorded the rest
            preferences.putString(SERVER_VERSION_KEY, value?.let { it.serverVersion.orEmpty() })
            preferences.putString(EXTENSIONS_KEY, value?.extensions?.sorted()?.joinToString(","))
        }

    /** Whether the server refused a token (error 41), so requests send the password instead. */
    private var sendPassword: Boolean
        get() = preferences.getBoolean(SEND_PASSWORD_KEY)
        set(value) = preferences.putBoolean(SEND_PASSWORD_KEY, value)

    /** The request credentials [authenticatedCredentials] stand for. */
    fun credentials(authenticatedCredentials: AuthenticatedCredentials): SubsonicCredentials = if (authenticatedCredentials.userId.isEmpty()) {
        SubsonicCredentials.ApiKey(authenticatedCredentials.accessToken)
    } else {
        SubsonicCredentials.Token(authenticatedCredentials.userId, authenticatedCredentials.accessToken)
    }

    /** How a request signs itself with [authenticatedCredentials]: a fresh salt each time [SubsonicAuth.parameters] is read. */
    fun auth(authenticatedCredentials: AuthenticatedCredentials): SubsonicAuth = SubsonicAuth(credentials(authenticatedCredentials), sendPassword)

    /**
     * Signs in with `ping`: a username and password, or, with no username, an API key in the password field. On success
     * saves the credentials and what the server says about itself ([serverInfo]). A server that refuses tokens (41) is
     * pinged again with the password, and remembered as such. A failure leaves a working session alone.
     */
    suspend fun authenticate(address: String, loginCredentials: LoginCredentials): Result<AuthenticatedCredentials> {
        logger.debug { "authenticate(address: $address)" }
        val authenticatedCredentials = AuthenticatedCredentials(accessToken = loginCredentials.password, userId = loginCredentials.username.trim())
        val credentials = credentials(authenticatedCredentials)
        var auth = SubsonicAuth(credentials)
        var ping = service.ping(address, auth)
        if ((ping as? NetworkResult.Failure)?.error is SubsonicError.TokenAuthNotSupported && credentials is SubsonicCredentials.Token) {
            auth = auth.copy(sendPassword = true)
            ping = service.ping(address, auth)
        }
        val response = when (ping) {
            is NetworkResult.Success -> ping.body
            is NetworkResult.Failure -> return Result.failure(ping.error)
        }
        val extensions = if (response.openSubsonic) {
            when (val result = service.openSubsonicExtensions(address, auth)) {
                is NetworkResult.Success -> result.body.map { it.name }.toSet()

                is NetworkResult.Failure -> {
                    logger.warn(result.error) { "Failed to read the server's OpenSubsonic extensions" }
                    emptySet()
                }
            }
        } else {
            emptySet()
        }
        sendPassword = auth.sendPassword
        serverInfo = SubsonicServerInfo(response.openSubsonic, response.type, response.serverVersion, extensions)
        credentialStore.authenticatedCredentials = authenticatedCredentials
        return Result.success(authenticatedCredentials)
    }

    /**
     * [request] signed with [authenticatedCredentials]. When the server refuses a token (41), it's made again with the
     * password, which later requests send too. A rejection of the credentials themselves signs out ([checkSession]).
     */
    suspend fun <T : Any> request(
        authenticatedCredentials: AuthenticatedCredentials,
        request: suspend (SubsonicAuth) -> NetworkResult<T>
    ): NetworkResult<T> {
        val auth = auth(authenticatedCredentials)
        var result = request(auth)
        if ((result as? NetworkResult.Failure)?.error is SubsonicError.TokenAuthNotSupported && auth.credentials is SubsonicCredentials.Token && !auth.sendPassword) {
            logger.info { "The server refused token authentication; sending the password" }
            sendPassword = true
            result = request(auth.copy(sendPassword = true))
        }
        return credentialStore.checkSession(authenticatedCredentials, result)
    }

    /** [request] signed with the stored credentials, or a failure when nothing's signed in. */
    suspend fun <T : Any> request(request: suspend (address: String, auth: SubsonicAuth) -> NetworkResult<T>): NetworkResult<T> {
        val address = getAddress() ?: return NetworkResult.Failure(IllegalStateException("No Subsonic server address"))
        val credentials = getAuthenticatedCredentials() ?: return NetworkResult.Failure(IllegalStateException("Not signed in to the Subsonic server"))
        return request(credentials) { auth -> request(address, auth) }
    }

    private companion object {
        const val OPEN_SUBSONIC_KEY = "subsonic_open_subsonic"
        const val SERVER_TYPE_KEY = "subsonic_server_type"
        const val SERVER_VERSION_KEY = "subsonic_server_version"
        const val EXTENSIONS_KEY = "subsonic_extensions"
        const val SEND_PASSWORD_KEY = "subsonic_legacy_password"
    }
}
