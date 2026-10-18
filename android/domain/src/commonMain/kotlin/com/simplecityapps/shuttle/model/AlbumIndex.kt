package com.simplecityapps.shuttle.model

/** Built once per library change and shared, so callers look songs up by id rather than rereading the library. */
class AlbumIndex(private val songs: Collection<AlbumIdentityTags>) {
    /** Each song's identity, by song id. */
    val identities: Map<Long, AlbumIdentity> = AlbumIdentityRule.resolve(songs)

    private val songIdsByAlbum: Map<AlbumGroupKey, List<Long>> by lazy {
        identities.entries.groupBy({ (_, identity) -> identity.groupKey }, { (songId, _) -> songId })
    }

    private val songIdsByArtist: Map<AlbumArtistGroupKey, List<Long>> by lazy {
        val byArtist = HashMap<AlbumArtistGroupKey, MutableList<Long>>()
        songs.forEach { tags ->
            val identity = identities[tags.songId] ?: return@forEach
            val artists = LinkedHashSet<AlbumArtistGroupKey>().apply {
                addAll(identity.albumArtistKeys)
                ArtistCredits.credits(tags, identity).mapTo(this) { credit -> credit.groupKey }
            }
            artists.forEach { key -> byArtist.getOrPut(key) { mutableListOf() } += tags.songId }
        }
        byArtist
    }

    /** Maps keys stored before the album identity rule; delete with [AlbumKeyRekey]. */
    val rekey: AlbumKeyRekey by lazy {
        AlbumKeyRekey(songs.mapNotNull { tags -> identities[tags.songId]?.let { tags to it } })
    }

    /** The ids of the songs of the album [key] names (excluded songs too); empty for none. */
    fun songIds(key: AlbumGroupKey): List<Long> = songIdsByAlbum[key].orEmpty()

    /**
     * The ids of the songs of the artist [key] names (excluded songs too): those of the albums they're the album artist
     * of, and those crediting them ([Song.isByArtist]); empty for none.
     */
    fun songIds(key: AlbumArtistGroupKey): List<Long> = songIdsByArtist[key].orEmpty()
}

/** The library's current [AlbumIndex]: one shared index, rebuilt only when the library's songs change. */
fun interface AlbumIndexProvider {
    suspend fun albumIndex(): AlbumIndex
}
