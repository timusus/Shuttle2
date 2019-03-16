package au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.lastfm

import au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.simplecityapps.mediaprovider.model.Album
import retrofit2.Call

class LastFmAlbumRemoteArtworkProvider(
    private val lastFm: LastFmService.LastFm,
    private val album: Album
) : RemoteArtworkProvider {
    override fun getCacheKey(): String {
        return "${album.albumArtistName}_${album.name}"
    }

    override fun getArtworkUri(): Call<out ArtworkUrlResult> {
        return lastFm.getLastFmAlbum(album.albumArtistName, album.name)
    }
}