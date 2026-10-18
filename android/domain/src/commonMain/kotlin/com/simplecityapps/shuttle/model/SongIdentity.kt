package com.simplecityapps.shuttle.model

/** The tags [AlbumIdentityRule] reads from this song. */
val Song.identityTags: AlbumIdentityTags
    get() = AlbumIdentityTags(
        songId = id,
        album = album,
        albumArtist = albumArtist,
        albumArtists = albumArtists,
        artists = artists,
        compilation = compilation,
        mbAlbumId = mbAlbumId,
        serverAlbumId = serverAlbumId,
        mediaProvider = mediaProvider,
        path = path,
        artistsTag = artistsTag,
        mbArtistIds = mbArtistIds,
        mbAlbumArtistIds = mbAlbumArtistIds,
        serverArtistIds = serverArtistIds,
        serverAlbumArtistIds = serverAlbumArtistIds
    )

/** These songs, each holding the album identity [AlbumIdentityRule] gives it among them: a whole library's songs. */
fun List<Song>.withAlbumIdentities(): List<Song> {
    // Resolved by position, not id: songs not yet stored all have id 0
    val identities = AlbumIdentityRule.resolve(mapIndexed { index, song -> song.identityTags.copy(songId = index.toLong()) })
    return mapIndexed { index, song -> song.copy(albumIdentity = identities.getValue(index.toLong())) }
}

/** These songs, each holding its identity in [identities] (the library's, resolved already); one not in it keeps its own. */
fun List<Song>.withAlbumIdentities(identities: Map<Long, AlbumIdentity>): List<Song> = map { song -> identities[song.id]?.let { song.copy(albumIdentity = it) } ?: song }

/** Whether this song is [key]'s: an album artist of its album, or credited on it. */
fun Song.isByArtist(key: AlbumArtistGroupKey?): Boolean = isAlbumArtist(key) || (key != null && artistCredits.any { it.groupKey == key })

/** Whether [key] is an album artist of this song's album ([AlbumIdentity.albumArtists]): it's among their own albums. */
fun Song.isAlbumArtist(key: AlbumArtistGroupKey?): Boolean = key != null && key in resolvedAlbumIdentity.albumArtistKeys

/** The media server's own id for the artist [key] on this song ([ArtistCredits.serverArtistId]): whose server image is theirs. */
fun Song.serverArtistId(key: AlbumArtistGroupKey): String? = ArtistCredits.serverArtistId(identityTags, resolvedAlbumIdentity, key)
