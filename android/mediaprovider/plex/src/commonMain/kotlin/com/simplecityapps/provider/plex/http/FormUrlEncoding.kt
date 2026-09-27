package com.simplecityapps.provider.plex.http

/**
 * [value] encoded as `application/x-www-form-urlencoded`, byte for byte what `java.net.URLEncoder.encode(value, "UTF-8")`
 * gives: letters, digits and `.-*_` stay, a space becomes `+`, and every other UTF-8 byte is percent-encoded.
 */
internal fun formUrlEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val char = (byte.toInt() and 0xFF).toChar()
        when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in ".-*_" -> append(char)
            char == ' ' -> append('+')
            else -> append('%').append(HEX_DIGITS[byte.toInt() shr 4 and 0xF]).append(HEX_DIGITS[byte.toInt() and 0xF])
        }
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"
