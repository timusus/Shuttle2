package com.simplecityapps.shuttle.model

/** Moves keys stored under the old name rule to the album their songs belong to now; delete once those have migrated. */
class AlbumKeyRekey(songs: Collection<Pair<AlbumIdentityTags, AlbumIdentity>>) {
    private val albums: Set<AlbumGroupKey> = songs.mapTo(HashSet()) { (_, identity) -> identity.groupKey }

    private val albumArtists: Set<AlbumArtistGroupKey> = songs.mapTo(HashSet()) { (_, identity) -> identity.albumArtistGroupKey }

    private val oldAlbums: Map<AlbumGroupKey, AlbumGroupKey> = songs
        .groupBy({ (tags, _) -> oldAlbumKey(tags) }, { (_, identity) -> identity.groupKey })
        .mapValues { (_, keys) -> keys.mostCommon() }

    private val oldAlbumArtists: Map<AlbumArtistGroupKey, AlbumArtistGroupKey> = songs
        .groupBy({ (tags, _) -> oldAlbumArtistKey(tags) }, { (_, identity) -> identity.albumArtistGroupKey })
        .mapValues { (_, keys) -> keys.mostCommon() }

    /** The album [key] names now: itself when it's current, else the album its songs moved to; null for neither. */
    fun album(key: AlbumGroupKey): AlbumGroupKey? = key.takeIf { it in albums } ?: oldAlbums[key]

    /** The album artist [key] names now; see [album]. */
    fun albumArtist(key: AlbumArtistGroupKey): AlbumArtistGroupKey? = key.takeIf { it in albumArtists } ?: oldAlbumArtists[key]

    /** [context] with its album or album artist moved; null when it names one that's neither current nor old. */
    fun context(context: PlayContext): PlayContext? = when (context) {
        is PlayContext.Album -> album(context.groupKey)?.let { PlayContext.Album(it) }
        is PlayContext.AlbumArtist -> albumArtist(context.groupKey)?.let { PlayContext.AlbumArtist(it) }
        else -> context
    }

    private fun <K> List<K>.mostCommon(): K = groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<K, Int>> { it.value }.thenBy { it.key.toString() })
        .first().key

    companion object {
        /** A key as the old name rule gave it: the album name and [oldAlbumArtistKey]. */
        fun oldAlbumKey(tags: AlbumIdentityTags): AlbumGroupKey = AlbumGroupKey(AlbumIdentityRule.albumKey(tags.album), oldAlbumArtistKey(tags))

        /** An album artist key as the old name rule gave it: the album artist tag, else every artist joined. */
        fun oldAlbumArtistKey(tags: AlbumIdentityTags): AlbumArtistGroupKey = AlbumArtistGroupKey(
            tags.albumArtist?.let(AlbumIdentityRule::artistKey)
                ?: tags.artists.joinToString(", ") { AlbumIdentityRule.artistKey(it) }.ifEmpty { null }
        )
    }
}
