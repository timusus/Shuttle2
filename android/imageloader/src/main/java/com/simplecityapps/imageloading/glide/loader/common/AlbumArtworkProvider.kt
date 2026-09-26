package com.simplecityapps.imageloading.glide.loader.common

import com.simplecityapps.shuttle.model.Album

open class AlbumArtworkProvider(private val album: Album) : ArtworkProvider {
    override fun getCacheKey(): String = "${album.albumArtist ?: album.friendlyArtistName}_${album.name}".withArtworkVersion(album.artworkVersion)
}
