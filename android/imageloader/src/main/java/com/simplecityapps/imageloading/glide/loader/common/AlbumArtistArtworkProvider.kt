package com.simplecityapps.imageloading.glide.loader.common

import com.simplecityapps.shuttle.model.AlbumArtist

open class AlbumArtistArtworkProvider(private val albumArtist: AlbumArtist) : ArtworkProvider {
    override fun getCacheKey(): String = (albumArtist.name ?: albumArtist.friendlyArtistName ?: "Unknown").withArtworkVersion(albumArtist.artworkVersion)
}
