package au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.lastfm

import au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.simplecityapps.mediaprovider.model.AlbumArtist
import retrofit2.Call

class LastFmAlbumArtistRemoteArtworkProvider(
    private val lastFm: LastFmService.LastFm,
    private val albumArtist: AlbumArtist
) : RemoteArtworkProvider {
    override fun getCacheKey(): String {
        return albumArtist.name
    }

    override fun getArtworkUri(): Call<out ArtworkUrlResult> {
        return lastFm.getLastFmArtist(albumArtist.name)
    }
}