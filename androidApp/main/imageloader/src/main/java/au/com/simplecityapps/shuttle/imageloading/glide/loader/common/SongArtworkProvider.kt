package au.com.simplecityapps.shuttle.imageloading.glide.loader.common

import com.simplecityapps.shuttle.model.Song


open class SongArtworkProvider(val song: Song) : ArtworkProvider {

    override fun getCacheKey(): String {
        return "${song.albumArtist ?: song.friendlyArtistName}_${song.album}_${song.name}"
    }
}