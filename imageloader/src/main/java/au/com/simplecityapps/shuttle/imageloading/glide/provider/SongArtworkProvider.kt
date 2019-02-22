package au.com.simplecityapps.shuttle.imageloading.glide.provider

import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.simplecityapps.mediaprovider.model.Song
import retrofit2.Call

class SongArtworkProvider(
    private val lastFm: LastFmService.LastFm,
    private val song: Song
) : ArtworkProvider {
    override fun getCacheKey(): String {
        return "${song.albumArtistName}_${song.name}"
    }

    override fun getArtworkUri(): Call<out ArtworkUrlResult> {
        return lastFm.getLastFmTrack(song.albumArtistName, song.name)
    }
}