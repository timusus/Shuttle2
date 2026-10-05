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
 * `emby` and `plex` read the keys each provider has always written, so existing sign-ins carry over; `subsonic` is new. [addressKey]
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

    // Guards the session keys, which are written together
    private val lock = Lock()

    /** The saved sign-in, for re-authenticating once the session expires. */
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

    /**
     * The signed-in session. Setting it replaces the stored session outright, as a sign-in does; a write that must not
     * clobber a newer session, or bring back a cleared one, goes through [compareAndSetAuthenticatedCredentials]. Every
     * read and write holds the same lock as [expireSession], so none of them sees or leaves a half-written session (#596).
     */
    var authenticatedCredentials: AuthenticatedCredentials?
        get() = lock.withLock { readAuthenticatedCredentials() }
        set(value) = lock.withLock { writeAuthenticatedCredentials(value) }

    /**
     * Replaces the stored session with [new] only while it is still [expected] (the same access token), returning
     * whether it did. So saving something learned about [expected], such as a refreshed download permission, never
     * overwrites a newer sign-in or brings back a session [expireSession] has cleared (#596).
     */
    fun compareAndSetAuthenticatedCredentials(
        expected: AuthenticatedCredentials,
        new: AuthenticatedCredentials?
    ): Boolean = lock.withLock {
        val current = readAuthenticatedCredentials()?.accessToken == expected.accessToken
        if (current) writeAuthenticatedCredentials(new)
        current
    }

    private fun readAuthenticatedCredentials(): AuthenticatedCredentials? {
        val accessToken = securePreferenceManager.getString(accessTokenKey) ?: return null
        val userId = securePreferenceManager.getString(userIdKey) ?: return null
        return AuthenticatedCredentials(accessToken, userId, securePreferenceManager.getBoolean(canDownloadKey))
    }

    private fun writeAuthenticatedCredentials(value: AuthenticatedCredentials?) {
        securePreferenceManager.putString(accessTokenKey, value?.accessToken)
        securePreferenceManager.putString(userIdKey, value?.userId)
        securePreferenceManager.putBoolean(canDownloadKey, value?.canDownload ?: false)
    }

    var address: String?
        get() = securePreferenceManager.getString(addressKey)
        set(value) {
            securePreferenceManager.putString(addressKey, value)
        }

    /** Forgets the server outright: its address, the saved login and the session, as removing it from Sources does. */
    fun clear() {
        lock.withLock { writeAuthenticatedCredentials(null) }
        loginCredentials = null
        address = null
    }

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Emits each time the server rejects the stored session mid-session and [expireSession] clears it (#577). The
     * next sync signs in again with the saved [loginCredentials]; without them (a Quick Connect session, say) the
     * user has to sign in again.
     */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    // Guarded by [lock]: the syncs running now, and whether one of their 401s cleared the session while they ran
    private var syncs = 0
    private var expiryPending = false

    /**
     * Clears the stored session and signals [sessionExpired], because the server answered a request made with
     * [rejected] with a 401. Does nothing, and returns false, when the stored session is no longer [rejected]: a
     * newer sign-in has replaced it, or another rejected request already cleared it. So a 401 that arrives late
     * never signs out a fresh session, and requests rejected together signal once. While a sync runs under
     * [deferringExpiry] the signal waits until it ends, and is dropped if the sync signed in again by then.
     */
    fun expireSession(rejected: AuthenticatedCredentials): Boolean {
        if (!compareAndSetAuthenticatedCredentials(expected = rejected, new = null)) return false
        val deferred =
            lock.withLock {
                if (syncs > 0) expiryPending = true
                syncs > 0
            }
        if (!deferred) _sessionExpired.tryEmit(Unit)
        return true
    }

    /**
     * Runs [block], a sync that signs in again when the server rejects its session, holding back [sessionExpired]
     * until it ends: it signals then only if the session is still cleared, so a session the sync renewed doesn't
     * tell the user they were signed out.
     */
    suspend fun <T> deferringExpiry(block: suspend () -> T): T {
        lock.withLock { syncs++ }
        try {
            return block()
        } finally {
            val settled =
                lock.withLock {
                    syncs--
                    (syncs == 0 && expiryPending).also { if (it) expiryPending = false }
                }
            if (settled && authenticatedCredentials == null) _sessionExpired.tryEmit(Unit)
        }
    }
}

/** [result], after [ServerCredentialStore.expireSession] when the server rejected [credentials] with a 401 (#577). */
fun <T : Any> ServerCredentialStore.checkSession(
    credentials: AuthenticatedCredentials,
    result: NetworkResult<T>
): NetworkResult<T> {
    val error = (result as? NetworkResult.Failure)?.error as? RemoteServiceHttpError
    if (error?.httpStatusCode == HttpStatusCode.Unauthorized) {
        expireSession(credentials)
    }
    return result
}
