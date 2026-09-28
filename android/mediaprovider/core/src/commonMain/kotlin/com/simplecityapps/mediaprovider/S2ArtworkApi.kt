package com.simplecityapps.mediaprovider

import kotlin.io.encoding.Base64

/**
 * The S2 artwork API: album and artist art looked up by name, the last place both apps try when the media server has none
 * (Android's `S2*ArtworkSource`s, iOS's `ArtworkUrls`). It answers 401 with a Basic challenge until given [USERNAME] and
 * [PASSWORD]; the credential is public by design, and stays out of the urls anyway.
 */
object S2ArtworkApi {
    const val HOST = "api.shuttlemusicplayer.app"
    const val USERNAME = "s2"
    const val PASSWORD = "aEqRKgkCbqALjEm9Eg7e7Qi5"

    /** The `Authorization` header value that answers the API's Basic challenge up front. */
    val authorization: String = "Basic " + Base64.encode("$USERNAME:$PASSWORD".encodeToByteArray())

    fun albumArtworkUrl(
        artist: String,
        album: String
    ): String = "https://$HOST/v1/artwork?artist=${artist.formUrlEncode()}&album=${album.formUrlEncode()}"

    fun artistArtworkUrl(artist: String): String = "https://$HOST/v1/artwork?artist=${artist.formUrlEncode()}"
}

/** `application/x-www-form-urlencoded`, byte for byte what `java.net.URLEncoder` produces for UTF-8. */
internal fun String.formUrlEncode(): String = buildString {
    for (byte in this@formUrlEncode.encodeToByteArray()) {
        val char = (byte.toInt() and 0xFF).toChar()
        when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-_.*" -> append(char)
            char == ' ' -> append('+')
            else -> append('%').append(HEX[byte.toInt() shr 4 and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
