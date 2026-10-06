package com.simplecityapps.shuttle.model

/**
 * The artwork model for a song shown as its album artist (#952): the artist's own image, which every song by them shares, else
 * [song]'s normal artwork. Only a real artist image counts, so an artist with none doesn't show their top album's cover.
 *
 * [artist] is a stand-in carrying what the artist image's lookup and cache key use, their name and group key.
 */
data class ArtistImageArtwork(
    val artist: AlbumArtist,
    val song: Song,
)

/**
 * The model that pictures [song] on the player, the mini player and the media session (#952): [song] itself, or, when
 * [artistImage] and the song has an album artist, an [ArtistImageArtwork] falling back to the song's own artwork.
 */
fun nowPlayingArtworkModel(
    song: Song,
    artistImage: Boolean,
): Any {
    if (!artistImage) return song
    val name = song.albumArtist?.takeIf { it.isNotBlank() } ?: return song
    val artist = AlbumArtist(
        name = name,
        artists = listOf(name),
        albumCount = 0,
        songCount = 0,
        playCount = 0,
        groupKey = song.albumArtistGroupKey,
        mediaProviders = listOf(song.mediaProvider)
    )
    return ArtistImageArtwork(artist, song)
}
