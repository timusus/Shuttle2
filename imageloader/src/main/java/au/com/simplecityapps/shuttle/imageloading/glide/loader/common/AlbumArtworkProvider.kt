package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.mediaprovider.model.Album

open class AlbumArtworkProvider(private val album: Album) : ArtworkProvider {

    override fun getCacheKey(): String {
        return "${album.friendlyAlbumArtistOrArtistName}_${album.name}"
    }
}