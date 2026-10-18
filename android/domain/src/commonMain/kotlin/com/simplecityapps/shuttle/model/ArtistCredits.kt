package com.simplecityapps.shuttle.model

/**
 * The artist credit rule (#637): which artists a song credits as its track artists, and whose artist page each is. With
 * [AlbumIdentityRule], which gives each album its album artist, it decides what an artist page lists: the albums they're
 * the album artist of (their own), then the albums they're only credited on (Appears On).
 *
 * - The ARTISTS multi-value tag (a server's artist list) names the artists, each value whole. Without one, the ARTIST
 *   tag does (a local file's already split on ";", "|" and " / " as it was read, #880; a server's as it sent it), each
 *   value split further on "; ", " / " and "feat." / "ft." / "featuring", as Navidrome does. "&" and a bare "/" (AC/DC)
 *   never split a name. Credits are paired with ids first, then deduplicated by key.
 * - The album artist is split only on "feat." ([AlbumIdentity.featuredArtists]): whoever it features is credited too.
 * - A credit carrying the album artist's own MusicBrainz or server artist id is that album artist, however it's spelt,
 *   so a variant spelling on their own album isn't an appearance. Ids pair with names only when there are as many of each.
 * - Any other credit belongs to its name's key ([AlbumIdentityRule.artistKey]): the key an album artist of that name has.
 */
object ArtistCredits {
    private val VARIOUS_ARTISTS_KEY = AlbumArtistGroupKey(AlbumIdentityRule.artistKey(AlbumIdentityRule.VARIOUS_ARTISTS))

    /** The artists [tags] credits, each once, in credit order; [identity] is the song's album identity. */
    fun credits(tags: AlbumIdentityTags, identity: AlbumIdentity): List<ArtistCredit> = creditsWithServerIds(tags, identity).map { (credit, _) -> credit }.distinctBy { it.groupKey }

    /**
     * The media server's own id for the artist [key] (#653): the song's album artist's when that's [key] and the song names
     * exactly one, else the id paired with [key]'s credit. Null when the song doesn't pin one down, and for "Various
     * Artists", who has no one face: a server image is then never borrowed from another artist on the same song (a duet
     * partner's, or a compilation track's performer for its album artist).
     */
    fun serverArtistId(tags: AlbumIdentityTags, identity: AlbumIdentity, key: AlbumArtistGroupKey): String? {
        if (key.key == null || key == VARIOUS_ARTISTS_KEY) return null
        val albumArtists = identity.albumArtistKeys
        if (key in albumArtists) {
            if (albumArtists.size == 1) return tags.serverAlbumArtistIds.single()
            return tags.serverAlbumArtistIds.pairedWith(albumArtists)?.get(albumArtists.indexOf(key))?.ifEmpty { null }
        }
        return creditsWithServerIds(tags, identity)
            .filter { (credit, _) -> credit.groupKey == key }
            .mapNotNull { (_, serverId) -> serverId?.takeIf { it.isNotEmpty() } }
            .distinct()
            .singleOrNull()
    }

    /** Each credit in order, before deduplication, with the server artist id paired with it (null when ids don't pair). */
    private fun creditsWithServerIds(tags: AlbumIdentityTags, identity: AlbumIdentity): List<Pair<ArtistCredit, String?>> {
        val multiValue = tags.artistsTag.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        val names = multiValue.ifEmpty { tags.artists.flatMap(ArtistNames::split) }
        // An id names the album artist only when the album has one
        val albumArtistKey = identity.albumArtistKeys.singleOrNull()
        val mbIds = tags.mbArtistIds.pairedWith(names)
        val serverIds = tags.serverArtistIds.pairedWith(names)
        val albumArtistMbId = tags.mbAlbumArtistIds.single()
        val albumArtistServerId = tags.serverAlbumArtistIds.single()
        val trackArtists = names.mapIndexed { index, name ->
            val isAlbumArtist = (albumArtistMbId != null && mbIds?.get(index)?.equals(albumArtistMbId, ignoreCase = true) == true) ||
                (albumArtistServerId != null && serverIds?.get(index) == albumArtistServerId)
            ArtistCredit(name, albumArtistKey?.takeIf { isAlbumArtist } ?: AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))) to serverIds?.get(index)
        }
        return trackArtists + identity.featuredArtists.map { name -> ArtistCredit(name, AlbumArtistGroupKey(AlbumIdentityRule.artistKey(name))) to null }
    }

    private fun List<String>?.pairedWith(names: List<*>): List<String>? = this?.map { it.trim() }?.takeIf { it.size == names.size }

    private fun List<String>?.single(): String? = this?.map { it.trim() }?.filter { it.isNotEmpty() }?.singleOrNull()
}
