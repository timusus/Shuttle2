package com.simplecityapps.shuttle.model

/**
 * A library's album identities ([AlbumIdentityRule]), resolved once over every song's identity tags: each song's
 * identity, the songs of each album and album artist, and the [AlbumKeyRekey] for keys stored before #637. Built by an
 * [AlbumIndexProvider], which keeps one until the library changes, so a caller asks it for songs by id rather than
 * reading the whole library.
 */
class AlbumIndex(songs: Collection<AlbumIdentityTags>) {
    /** Each song's identity, by song id. */
    val identities: Map<Long, AlbumIdentity> = AlbumIdentityRule.resolve(songs)

    private val songIdsByAlbum: Map<AlbumGroupKey, List<Long>> by lazy {
        identities.entries.groupBy({ (_, identity) -> identity.groupKey }, { (songId, _) -> songId })
    }

    private val songIdsByAlbumArtist: Map<AlbumArtistGroupKey, List<Long>> by lazy {
        identities.entries.groupBy({ (_, identity) -> identity.albumArtistGroupKey }, { (songId, _) -> songId })
    }

    /** For keys stored before the album identity rule (#637); to be deleted with [AlbumKeyRekey]. */
    val rekey: AlbumKeyRekey by lazy {
        AlbumKeyRekey(songs.mapNotNull { tags -> identities[tags.songId]?.let { tags to it } })
    }

    /** The ids of the songs of the album [key] names (excluded songs too); empty for none. */
    fun songIds(key: AlbumGroupKey): List<Long> = songIdsByAlbum[key].orEmpty()

    /** The ids of the songs of the album artist [key] names (excluded songs too); empty for none. */
    fun songIds(key: AlbumArtistGroupKey): List<Long> = songIdsByAlbumArtist[key].orEmpty()
}

/** The library's current [AlbumIndex]: one shared index, rebuilt only when the library's songs change. */
fun interface AlbumIndexProvider {
    suspend fun albumIndex(): AlbumIndex
}
