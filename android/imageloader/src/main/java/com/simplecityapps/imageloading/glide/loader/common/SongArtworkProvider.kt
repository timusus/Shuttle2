package com.simplecityapps.imageloading.glide.loader.common

import com.simplecityapps.imageloading.coil.artworkCacheKey
import com.simplecityapps.shuttle.model.Song

open class SongArtworkProvider(val song: Song) : ArtworkProvider {
    override fun getCacheKey(): String = song.artworkCacheKey()
}
