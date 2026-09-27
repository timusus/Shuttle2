package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import dev.zacsweers.metro.Inject

/**
 * The signed-in Last.fm session key ([authspec](https://www.last.fm/api/authspec)), which doesn't expire on
 * its own. Sign-in itself (the browser approval flow) is a later slice; this store only holds the result and
 * clears it when the flush worker sees an invalid-session error, so the next flush is a no-op until the user
 * signs in again.
 */
interface LastFmSessionStore {
    var sessionKey: String?

    fun signOut()
}

class SecurePreferenceLastFmSessionStore
@Inject
constructor(
    private val securePreferenceManager: SecurePreferenceManager
) : LastFmSessionStore {
    override var sessionKey: String?
        get() = securePreferenceManager.getString(KEY_SESSION)
        set(value) = securePreferenceManager.putString(KEY_SESSION, value)

    override fun signOut() {
        sessionKey = null
    }

    companion object {
        private const val KEY_SESSION = "lastfm_session_key"
    }
}
