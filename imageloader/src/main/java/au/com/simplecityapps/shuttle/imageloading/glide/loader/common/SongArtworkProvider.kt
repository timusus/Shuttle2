package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.mediaprovider.model.Song

open class SongArtworkProvider(private val song: Song) : ArtworkProvider {

    override fun getCacheKey(): String {
        return "${song.albumArtist}_${song.name}"
    }
}