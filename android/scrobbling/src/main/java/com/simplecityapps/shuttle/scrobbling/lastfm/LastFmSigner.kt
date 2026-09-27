package com.simplecityapps.shuttle.scrobbling.lastfm

import java.security.MessageDigest

/**
 * Signs a Last.fm API call ([authspec](https://www.last.fm/api/authspec)): every param except `format` and
 * `callback`, sorted by name, concatenated as `namevalue` pairs with no separator, the shared secret appended,
 * then MD5 hex-digested.
 */
object LastFmSigner {
    fun sign(
        params: Map<String, String>,
        sharedSecret: String
    ): String {
        val signable = params
            .filterKeys { it != "format" && it != "callback" }
            .toSortedMap()
            .entries
            .joinToString(separator = "") { (key, value) -> "$key$value" }
        return md5Hex(signable + sharedSecret)
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
