package com.simplecityapps.shuttle.model

/** An artist a song credits: [name] as its tags spell it, and [groupKey], the artist page it belongs to. */
data class ArtistCredit(
    val name: String,
    val groupKey: AlbumArtistGroupKey
)

/** How an artist tag's value splits into artists, for [ArtistCredits] (track artists) and [AlbumIdentityRule] (album artists). */
object ArtistNames {
    private const val FEATURING_PATTERN = "\\s+[(\\[]?(?:feat\\.?|ft\\.?|featuring)\\s+"

    private val SEPARATOR = Regex("\\s*;\\s+|\\s+/\\s+|$FEATURING_PATTERN", RegexOption.IGNORE_CASE)

    /**
     * "feat." and "ft." with their dot, or "featuring": an album artist is a band's name more often than a track artist,
     * and "Little Feat" or "The Feat Band" is no featuring.
     */
    private val FEATURING = Regex("\\s+[(\\[]?(?:feat\\.|ft\\.|featuring)\\s+", RegexOption.IGNORE_CASE)

    /** One ARTIST value's artists: "A feat. B" is A and B, "A (feat. B)" too; "A & B" and "AC/DC" are one each. */
    fun split(artist: String): List<String> = SEPARATOR.split(artist).cleaned()

    /**
     * An album artist's artist, then whoever it features: "A feat. B" and "A (feat. B)" are A then B. Only "feat." splits
     * an album artist: "A; B", "A / B" and "A & B" are one each.
     */
    fun splitFeaturing(albumArtist: String): List<String> = FEATURING.split(albumArtist).cleaned()

    private fun List<String>.cleaned(): List<String> = map { part -> part.trim().withoutUnopenedBracket().trim() }.filter { it.isNotEmpty() }

    /** "B)", left of "A (feat. B)", without its closing bracket. */
    private fun String.withoutUnopenedBracket(): String = when {
        endsWith(')') && count { it == '(' } < count { it == ')' } -> dropLast(1)
        endsWith(']') && count { it == '[' } < count { it == ']' } -> dropLast(1)
        else -> this
    }
}
