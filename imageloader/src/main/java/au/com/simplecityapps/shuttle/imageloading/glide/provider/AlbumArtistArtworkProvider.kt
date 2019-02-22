package au.com.simplecityapps.shuttle.imageloading.glide.provider

import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.simplecityapps.mediaprovider.model.AlbumArtist
import retrofit2.Call

class AlbumArtistArtworkProvider(
    private val lastFm: LastFmService.LastFm,
    private val albumArtist: AlbumArtist
) : ArtworkProvider {
    override fun getCacheKey(): String {
        return albumArtist.name
    }

    override fun getArtworkUri(): Call<out ArtworkUrlResult> {
        return lastFm.getLastFmArtist(albumArtist.name)
    }
}