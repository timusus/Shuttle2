package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.mediaprovider.model.Song

open class SongArtworkProvider(val song: Song) : ArtworkProvider {

    override fun getCacheKey(): String {
        return "${song.friendlyAlbumArtistOrArtistName}_${song.album}_${song.name}"
    }
}