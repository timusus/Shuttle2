package au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.lastfm

import au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.simplecityapps.mediaprovider.model.Song
import retrofit2.Call

class LastFmSongRemoteArtworkProvider(
    private val lastFm: LastFmService.LastFm,
    private val song: Song
) : RemoteArtworkProvider {
    override fun getCacheKey(): String {
        return "${song.albumArtistName}_${song.albumName}"
    }

    override fun getArtworkUri(): Call<out ArtworkUrlResult> {
        return lastFm.getLastFmAlbum(song.albumArtistName, song.albumName)
    }
}