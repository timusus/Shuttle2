package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.shuttle.model.Album

open class AlbumArtworkProvider(private val album: Album) : ArtworkProvider {
    override fun getCacheKey(): String {
        return "${album.albumArtist ?: album.friendlyArtistName}_${album.name}"
    }
}
