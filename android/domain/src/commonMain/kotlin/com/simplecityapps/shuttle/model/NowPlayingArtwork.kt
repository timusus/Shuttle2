package com.simplecityapps.shuttle.model

/** Only a real artist image stands in for the song's artwork, never their top album's cover. */
data class ArtistImageArtwork(
    val artist: AlbumArtist,
    val song: Song,
)

/** The album artist's image stands in for the song's artwork only when [artistImage] is on. */
fun nowPlayingArtworkModel(
    song: Song,
    artistImage: Boolean,
): Any {
    if (!artistImage) return song
    if (song.albumArtist.isNullOrBlank() && song.albumArtists.orEmpty().all { it.isBlank() }) return song
    // The primary album artist: an album of several pictures its first, "A feat. B"'s pictures A
    val primary = song.resolvedAlbumIdentity.albumArtists.firstOrNull()?.takeIf { it.name.isNotBlank() } ?: return song
    val artist = AlbumArtist(
        name = primary.name,
        artists = listOf(primary.name),
        albumCount = 0,
        songCount = 0,
        playCount = 0,
        groupKey = primary.groupKey,
        mediaProviders = listOf(song.mediaProvider)
    )
    return ArtistImageArtwork(artist, song)
}
