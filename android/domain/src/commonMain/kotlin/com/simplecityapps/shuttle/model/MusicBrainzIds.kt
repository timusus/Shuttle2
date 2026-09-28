package com.simplecityapps.shuttle.model

/**
 * The MusicBrainz ids in [values], lower-cased, in order and without repeats. A tag holding several ids may join them
 * ('/' from ID3v2.3 taggers, ';' from others), and servers hand the tag on as written, so each id is found by its UUID
 * form rather than by splitting. Anything else in the values is ignored.
 */
fun musicBrainzIds(values: List<String>): List<String> = values.flatMap { value -> MUSICBRAINZ_ID.findAll(value).map { match -> match.value.lowercase() } }.distinct()

/** The MusicBrainz ids in [value]: see the list form. */
fun musicBrainzIds(value: String?): List<String> = musicBrainzIds(listOfNotNull(value))

private val MUSICBRAINZ_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
