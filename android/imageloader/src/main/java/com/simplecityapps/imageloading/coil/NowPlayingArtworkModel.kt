package com.simplecityapps.imageloading.coil

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.albumArtistStandIn
import com.simplecityapps.shuttle.settings.NowPlayingImage

/**
 * The image-loader model that pictures [song] on the media session (#952): the song itself, or, for
 * [NowPlayingImage.ArtistImage], its album artist, whose image every one of their songs then shares. The artist model
 * resolves through the shared artist rule (own image, the online lookup the artwork settings allow, then their top album's
 * cover), so an artist with no image still shows artwork; a song with no album artist shows its own.
 */
fun nowPlayingArtworkModel(
    song: Song,
    image: NowPlayingImage
): Any = when (image) {
    NowPlayingImage.AlbumArt -> song
    NowPlayingImage.ArtistImage -> song.albumArtistStandIn() ?: song
}
