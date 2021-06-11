package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.mediaprovider.model.AlbumArtist

open class AlbumArtistArtworkProvider(private val albumArtist: AlbumArtist) : ArtworkProvider {

    override fun getCacheKey(): String {
        return albumArtist.name ?: albumArtist.friendlyArtistName ?: "Unknown"
    }
}