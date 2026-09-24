package com.simplecityapps.playback.chromecast

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The secret every URL the phone's [HttpServer] serves carries. The server listens on the whole network with no other
 * check, and a remote-provider song's stream is a redirect to a URL holding the provider's credential, so a request
 * without this session's key gets nothing. A new key comes with each Cast session.
 */
class CastStreams {
    private val random = SecureRandom()

    @Volatile
    var key: String = newKey()
        private set

    /** Replaces the key as a Cast session starts, so no URL from an earlier session is served. */
    fun newSession() {
        key = newKey()
    }

    /** Whether [candidate] is this session's key, compared in constant time. */
    fun isValid(candidate: String?): Boolean = candidate != null && MessageDigest.isEqual(candidate.toByteArray(), key.toByteArray())

    private fun newKey(): String = ByteArray(KEY_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_BYTES = 16
    }
}
