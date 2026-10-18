package com.simplecityapps.mediaprovider

/** Splits one ARTIST tag value; "/" splits only when spaced, so "AC/DC" stays whole, and repeats are kept to stay paired with MUSICBRAINZ_ARTISTID. */
fun splitArtistTag(value: String): List<String> = ARTIST_SEPARATOR.split(value).map { it.trim() }.filter { it.isNotEmpty() }

private val ARTIST_SEPARATOR = Regex("[;|]|\\s+/\\s+")
