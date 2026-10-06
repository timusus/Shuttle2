package com.simplecityapps.shuttle.model

/**
 * A stand-in for the song's album artist, for a surface that holds only the song yet pictures the artist (#952): it carries
 * what the artist image's lookup and cache key use, their name and group key, so every song by them resolves to the same image.
 * Null for a song with no album artist, which has no artist to picture.
 */
fun Song.albumArtistStandIn(): AlbumArtist? {
    val name = albumArtist?.takeIf { it.isNotBlank() } ?: return null
    return AlbumArtist(
        name = name,
        artists = listOf(name),
        albumCount = 0,
        songCount = 0,
        playCount = 0,
        groupKey = albumArtistGroupKey,
        mediaProviders = listOf(mediaProvider)
    )
}
