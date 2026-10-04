package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A signed-in Last.fm session ([authspec](https://www.last.fm/api/authspec)); the key doesn't expire on its own. */
data class LastFmSession(
    val key: String,
    val username: String
)

/**
 * The Last.fm session, and the token of a sign-in waiting for the user's approval on last.fm, kept in encrypted
 * prefs so both survive the process dying while the user is in the browser. The flush worker clears the session
 * when it sees an invalid-session error, so the next flush is a no-op until the user signs in again.
 */
interface LastFmSessionStore {
    val session: StateFlow<LastFmSession?>

    val pendingToken: StateFlow<String?>

    fun savePendingToken(token: String?)

    /** Stores [session] and drops the pending token it came from. */
    fun signIn(session: LastFmSession)

    /** Forgets the session and any pending token. Queued scrobbles are the caller's to keep or drop. */
    fun signOut()
}

@SingleIn(AppScope::class)
class SecurePreferenceLastFmSessionStore
@Inject
constructor(
    private val securePreferenceManager: SecurePreferenceManager
) : LastFmSessionStore {
    private val _session = MutableStateFlow(readSession())
    override val session: StateFlow<LastFmSession?> = _session.asStateFlow()

    private val _pendingToken = MutableStateFlow(securePreferenceManager.getString(KEY_PENDING_TOKEN))
    override val pendingToken: StateFlow<String?> = _pendingToken.asStateFlow()

    override fun savePendingToken(token: String?) {
        securePreferenceManager.putString(KEY_PENDING_TOKEN, token)
        _pendingToken.value = token
    }

    override fun signIn(session: LastFmSession) {
        securePreferenceManager.putString(KEY_SESSION, session.key)
        securePreferenceManager.putString(KEY_USERNAME, session.username)
        _session.value = session
        savePendingToken(null)
    }

    override fun signOut() {
        securePreferenceManager.putString(KEY_SESSION, null)
        securePreferenceManager.putString(KEY_USERNAME, null)
        _session.value = null
        savePendingToken(null)
    }

    private fun readSession(): LastFmSession? {
        val key = securePreferenceManager.getString(KEY_SESSION) ?: return null
        return LastFmSession(key = key, username = securePreferenceManager.getString(KEY_USERNAME).orEmpty())
    }

    companion object {
        private const val KEY_SESSION = "lastfm_session_key"
        private const val KEY_USERNAME = "lastfm_username"
        private const val KEY_PENDING_TOKEN = "lastfm_pending_token"
    }
}
