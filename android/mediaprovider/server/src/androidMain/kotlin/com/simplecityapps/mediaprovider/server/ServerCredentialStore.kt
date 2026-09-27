package com.simplecityapps.mediaprovider.server

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A media server's address and credentials, kept in [SecurePreferenceManager] under `<prefix>_*` keys: `jellyfin`,
 * `emby` and `plex` read the keys each provider has always written, so existing sign-ins carry over. [addressKey]
 * is there for Plex, which has always stored its address as `plex_host`.
 */
class ServerCredentialStore(
    private val securePreferenceManager: SecurePreferenceManager,
    prefix: String,
    private val addressKey: String = "${prefix}_address"
) {
    private val userNameKey = "${prefix}_username"
    private val passwordKey = "${prefix}_pass"
    private val accessTokenKey = "${prefix}_access_token"
    private val userIdKey = "${prefix}_user_id"
    private val canDownloadKey = "${prefix}_can_download"

    /** The saved sign-in, for re-authenticating once the session expires. Never holds an [LoginCredentials.authCode]. */
    var loginCredentials: LoginCredentials?
        get() {
            val userName = securePreferenceManager.getString(userNameKey) ?: return null
            val password = securePreferenceManager.getString(passwordKey) ?: return null
            return LoginCredentials(userName, password)
        }
        set(value) {
            securePreferenceManager.putString(userNameKey, value?.username)
            securePreferenceManager.putString(passwordKey, value?.password)
        }

    var authenticatedCredentials: AuthenticatedCredentials?
        get() {
            val accessToken = securePreferenceManager.getString(accessTokenKey) ?: return null
            val userId = securePreferenceManager.getString(userIdKey) ?: return null
            return AuthenticatedCredentials(accessToken, userId, securePreferenceManager.getBoolean(canDownloadKey))
        }
        set(value) {
            securePreferenceManager.putString(accessTokenKey, value?.accessToken)
            securePreferenceManager.putString(userIdKey, value?.userId)
            securePreferenceManager.putBoolean(canDownloadKey, value?.canDownload ?: false)
        }

    var address: String?
        get() = securePreferenceManager.getString(addressKey)
        set(value) {
            securePreferenceManager.putString(addressKey, value)
        }

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Emits each time the server rejects the stored session mid-session and [expireSession] clears it (#577). The
     * next sync signs in again with the saved [loginCredentials]; without them (a Quick Connect session, say) the
     * user has to sign in again.
     */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /**
     * Clears the stored session and signals [sessionExpired], because the server answered a request made with
     * [rejected] with a 401. Does nothing, and returns false, when the stored session is no longer [rejected]: a
     * newer sign-in has replaced it, or another rejected request already cleared it. So a 401 that arrives late
     * never signs out a fresh session, and requests rejected together signal once.
     */
    fun expireSession(rejected: AuthenticatedCredentials): Boolean {
        synchronized(this) {
            if (authenticatedCredentials?.accessToken != rejected.accessToken) return false
            authenticatedCredentials = null
        }
        _sessionExpired.tryEmit(Unit)
        return true
    }
}

/** [result], after [ServerCredentialStore.expireSession] when the server rejected [credentials] with a 401 (#577). */
fun <T : Any> ServerCredentialStore.checkSession(
    credentials: AuthenticatedCredentials,
    result: NetworkResult<T>
): NetworkResult<T> {
    val error = (result as? NetworkResult.Failure)?.error as? RemoteServiceHttpError
    checkSession(credentials, error?.httpStatusCode?.value)
    return result
}

/** Expires the session [credentials] when the server answered a request made with them with [statusCode] 401 (#577). */
fun ServerCredentialStore.checkSession(
    credentials: AuthenticatedCredentials,
    statusCode: Int?
) {
    if (statusCode == HttpStatusCode.Unauthorized.value) {
        expireSession(credentials)
    }
}
