package com.simplecityapps.localmediaprovider.local.provider.mediastore

internal data class DiscTrack(val disc: Int, val track: Int)

/**
 * MediaStore's TRACK column encodes disc*1000 + track (e.g. 2005 = disc 2, track 5). On API 30+
 * the DISC_NUMBER column is authoritative when present and wins over the encoded value.
 */
internal fun decodeDiscTrack(
    rawTrack: Int,
    discNumberColumnValue: String?
): DiscTrack {
    val encoded =
        if (rawTrack >= 1000) {
            DiscTrack(disc = rawTrack / 1000, track = rawTrack % 1000)
        } else {
            DiscTrack(disc = 1, track = rawTrack)
        }

    val discNumber = parseDiscNumber(discNumberColumnValue)
    return if (discNumber != null) encoded.copy(disc = discNumber) else encoded
}

internal fun parseDiscNumber(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    return value.substringBefore('/').trim().toIntOrNull()?.takeIf { it > 0 }
}
