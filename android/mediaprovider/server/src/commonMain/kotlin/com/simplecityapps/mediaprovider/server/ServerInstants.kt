package com.simplecityapps.mediaprovider.server

import kotlin.time.Instant

/**
 * This instant cut to whole milliseconds, the precision the library stores an instant at. Jellyfin and Emby report times to
 * seven fractional digits, so a song mapped with them as they are would never equal the same song read back from the library,
 * and every sync would rewrite it; mapping them at this precision keeps the comparison exact.
 */
fun Instant.atStoredPrecision(): Instant = Instant.fromEpochMilliseconds(toEpochMilliseconds())

/** A server's ISO 8601 time at the library's precision ([atStoredPrecision]); null if it's absent or can't be read. */
fun parseServerInstant(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }?.atStoredPrecision()
