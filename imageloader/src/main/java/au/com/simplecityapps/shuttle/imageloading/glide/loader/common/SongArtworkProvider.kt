package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.model.friendlyAlbumArtistOrArtistName

open class SongArtworkProvider(private val song: Song) : ArtworkProvider {

    override fun getCacheKey(): String {
        return "${song.friendlyAlbumArtistOrArtistName}_${song.album}_${song.name}"
    }
}