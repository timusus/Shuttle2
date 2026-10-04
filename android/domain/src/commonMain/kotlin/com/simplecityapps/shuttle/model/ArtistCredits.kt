package com.simplecityapps.shuttle.model

/** An artist a song credits (#637): [name] as its tags spell it, and [groupKey], the artist page it belongs to. */
data class ArtistCredit(
    val name: String,
    val groupKey: AlbumArtistGroupKey
)

/**
 * The artist credit rule (#637): which artists a song credits as its track artists, and whose artist page each is. With
 * [AlbumIdentityRule], which gives each album its album artist, it decides what an artist page lists: the albums they're
 * the album artist of (their own), then the albums they're only credited on (Appears On).
 *
 * - The ARTISTS multi-value tag (a server's artist list) names the artists, each value whole. Without one, the ARTIST
 *   tag does (a local file's already split on ";", "|" and " / " as it was read, #880; a server's as it sent it), each
 *   value split further on "; ", " / " and "feat." / "ft." / "featuring", as Navidrome does. "&" and a bare "/" (AC/DC)
 *   never split a name. The album artist is never split. Credits are paired with ids first, then deduplicated by key.
 * - A credit carrying the album artist's own MusicBrainz or server artist id is that album artist, however it's spelt,
 *   so a variant spelling on their own album isn't an appearance. Ids pair with names only when there are as many of each.
 * - Any other credit belongs to its name's key ([AlbumIdentityRule.artistKey]): the key an album artist of that name has.
 */
object ArtistCredits {
    private val SEPARATOR = Regex("\\s*;\\s+|\\s+/\\s+|\\s+[(\\[]?(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE)

    /** The artists [tags] credits, each once, in credit order; [identity] is the song's album identity. */
    fun credits(tags: AlbumIdentityTags, identity: AlbumIdentity): List<ArtistCredit> {
        val multiValue = tags.artistsTag.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val names = multiValue.ifEmpty { tags.artists.flatMap(::split) }
        val albumArtistKey = identity.albumArtistGroupKey
        val mbIds = tags.mbArtistIds.pairedWith(names)
        val serverIds = tags.serverArtistIds.pairedWith(names)
        val albumArtistMbId = tags.mbAlbumArtistIds.single()
        val albumArtistServerId = tags.serverAlbumArtistIds.single()
        return names.mapIndexed { index, name ->
            val isAlbumArtist = (albumArtistMbId != null && mbIds?.get(index)?.equals(albumArtistMbId, ignoreCase = true) == true) ||
                (albumArtistServerId != null && serverIds?.get(index) == albumArtistServerId)
            ArtistCredit(name, if (isAlbumArtist) albumArtistKey else AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name)))
        }.distinctBy { it.groupKey }
    }

    /** One ARTIST value's artists: "A feat. B" is A and B, "A (feat. B)" too; "A & B" and "AC/DC" are one each. */
    fun split(artist: String): List<String> = SEPARATOR.split(artist)
        .map { part -> part.trim().withoutUnopenedBracket().trim() }
        .filter { it.isNotEmpty() }

    /** "B)", left of "A (feat. B)", without its closing bracket. */
    private fun String.withoutUnopenedBracket(): String = when {
        endsWith(')') && count { it == '(' } < count { it == ')' } -> dropLast(1)
        endsWith(']') && count { it == '[' } < count { it == ']' } -> dropLast(1)
        else -> this
    }

    private fun List<String>?.pairedWith(names: List<String>): List<String>? = this?.map { it.trim() }?.takeIf { it.size == names.size }

    private fun List<String>?.single(): String? = this?.map { it.trim() }?.filter { it.isNotEmpty() }?.singleOrNull()
}

/** Whether this song is [key]'s: its album's album artist, or credited on it. */
fun Song.isByArtist(key: AlbumArtistGroupKey?): Boolean = albumArtistGroupKey == key || (key != null && artistCredits.any { it.groupKey == key })
