package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A signed-in ListenBrainz account: the user token pasted from listenbrainz.org/settings, which doesn't expire on its own. */
data class ListenBrainzAccount(
    val token: String,
    val username: String
)

/** The ListenBrainz token in encrypted prefs. The flush worker clears it when ListenBrainz rejects it, so the next flush is a no-op until the user signs in again. */
interface ListenBrainzSessionStore {
    val account: StateFlow<ListenBrainzAccount?>

    /** The username of the last account that signed in, kept through [signOut] so the next sign-in can tell whether queued scrobbles are another account's. */
    val lastUsername: String?

    fun signIn(account: ListenBrainzAccount)

    /** Forgets the token, but not [lastUsername]. Queued scrobbles are the caller's to keep or drop. */
    fun signOut()
}

@SingleIn(AppScope::class)
class SecurePreferenceListenBrainzSessionStore
@Inject
constructor(
    private val securePreferenceManager: SecurePreferenceManager
) : ListenBrainzSessionStore {
    private val _account = MutableStateFlow(readAccount())
    override val account: StateFlow<ListenBrainzAccount?> = _account.asStateFlow()

    override val lastUsername: String? get() = securePreferenceManager.getString(KEY_LAST_USERNAME)

    override fun signIn(account: ListenBrainzAccount) {
        securePreferenceManager.putString(KEY_TOKEN, account.token)
        securePreferenceManager.putString(KEY_USERNAME, account.username)
        securePreferenceManager.putString(KEY_LAST_USERNAME, account.username)
        _account.value = account
    }

    override fun signOut() {
        securePreferenceManager.putString(KEY_TOKEN, null)
        securePreferenceManager.putString(KEY_USERNAME, null)
        _account.value = null
    }

    private fun readAccount(): ListenBrainzAccount? {
        val token = securePreferenceManager.getString(KEY_TOKEN) ?: return null
        return ListenBrainzAccount(token = token, username = securePreferenceManager.getString(KEY_USERNAME).orEmpty())
    }

    private companion object {
        const val KEY_TOKEN = "listenbrainz_token"
        const val KEY_USERNAME = "listenbrainz_username"
        const val KEY_LAST_USERNAME = "listenbrainz_last_username"
    }
}
