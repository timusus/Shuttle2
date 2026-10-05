package com.simplecityapps.mediaprovider

/**
 * One ARTIST tag value's artists, for sources that put several in one string (#880): ";" and "|" separate wherever they stand,
 * "/" only with whitespace either side (" / "), so "AC/DC" stays whole; "&" and "," never separate ("Earth, Wind & Fire").
 * Repeats are kept, so the artists stay paired 1:1 with MUSICBRAINZ_ARTISTID; ArtistCredits credits each artist once.
 */
fun splitArtistTag(value: String): List<String> = ARTIST_SEPARATOR.split(value).map { it.trim() }.filter { it.isNotEmpty() }

private val ARTIST_SEPARATOR = Regex("[;|]|\\s+/\\s+")
