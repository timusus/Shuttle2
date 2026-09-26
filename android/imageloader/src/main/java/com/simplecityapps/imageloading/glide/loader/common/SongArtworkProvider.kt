package com.simplecityapps.imageloading.glide.loader.common

import com.simplecityapps.shuttle.model.Song

open class SongArtworkProvider(val song: Song) : ArtworkProvider {
    override fun getCacheKey(): String = "${song.albumArtist ?: song.friendlyArtistName}_${song.album}_${song.name}".withArtworkVersion(song.artworkVersion)
}
