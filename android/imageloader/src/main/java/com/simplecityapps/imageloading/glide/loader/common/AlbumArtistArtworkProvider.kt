package com.simplecityapps.imageloading.glide.loader.common

import com.simplecityapps.imageloading.coil.artworkCacheKey
import com.simplecityapps.shuttle.model.AlbumArtist

open class AlbumArtistArtworkProvider(private val albumArtist: AlbumArtist) : ArtworkProvider {
    override fun getCacheKey(): String = albumArtist.artworkCacheKey()
}
