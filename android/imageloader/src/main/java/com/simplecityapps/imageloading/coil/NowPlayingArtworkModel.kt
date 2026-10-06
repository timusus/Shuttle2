package com.simplecityapps.imageloading.coil

import com.simplecityapps.shuttle.model.ArtistImageArtwork
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.nowPlayingArtworkModel
import com.simplecityapps.shuttle.settings.NowPlayingImage

/** The image-loader [model] that pictures a song on the media session (#952), and the [cacheKey] identifying what it resolves to. */
class NowPlayingArtwork(
    val model: Any,
    val cacheKey: String,
)

/**
 * [song]'s [NowPlayingArtwork] for the Now playing artwork setting [image]: the one selection ([nowPlayingArtworkModel]) the
 * player's screens use too, keyed so a song falling back to its own art doesn't share an entry with the artist's image.
 */
fun nowPlayingArtwork(
    song: Song,
    image: NowPlayingImage
): NowPlayingArtwork {
    val model = nowPlayingArtworkModel(song, image == NowPlayingImage.ArtistImage)
    return NowPlayingArtwork(model, if (model is ArtistImageArtwork) model.artworkCacheKey() else song.artworkCacheKey())
}
