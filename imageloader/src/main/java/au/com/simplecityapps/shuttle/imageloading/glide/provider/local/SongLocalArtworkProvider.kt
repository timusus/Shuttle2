package au.com.simplecityapps.shuttle.imageloading.glide.provider.local

import com.simplecityapps.mediaprovider.model.Song

class SongLocalArtworkProvider(val song: Song) : LocalArtworkProvider {

    override fun getPath(): String {
        return song.path
    }

    override fun getCacheKey(): String {
        return "${song.albumArtistName}_${song.name}"
    }
}