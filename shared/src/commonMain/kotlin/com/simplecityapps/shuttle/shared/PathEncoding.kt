package com.simplecityapps.shuttle.shared

/**
 * This path with each segment percent-encoded and the slashes kept, for a URL Swift parses: spaces, non-ASCII letters
 * and reserved characters escaped as UTF-8 bytes.
 */
internal fun String.percentEncodedPath(): String = split('/').joinToString("/") { it.percentEncoded() }

private fun String.percentEncoded(): String = buildString {
    for (byte in this@percentEncoded.encodeToByteArray()) {
        val char = byte.toInt().toChar()
        if (byte >= 0 && (char.isLetterOrDigit() || char in UNRESERVED)) {
            append(char)
        } else {
            append('%')
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

private const val UNRESERVED = "-._~"
private const val HEX = "0123456789ABCDEF"
