package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtistArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.AlbumArtist
import retrofit2.Call
import java.io.InputStream

class LastFmRemoteAlbumArtistArtworkModelLoader(
    private val lastFm: LastFmService.LastFm,
    private val remoteArtworkModelLoader: RemoteArtworkModelLoader
) : ModelLoader<AlbumArtist, InputStream> {

    override fun buildLoadData(model: AlbumArtist, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return remoteArtworkModelLoader.buildLoadData(LastFmAlbumArtistRemoteArtworkProvider(lastFm, model), width, height, options)
    }

    override fun handles(model: AlbumArtist): Boolean {
        return true
    }


    class Factory(
        private val lastFm: LastFmService.LastFm
    ) : ModelLoaderFactory<AlbumArtist, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtist, InputStream> {
            return LastFmRemoteAlbumArtistArtworkModelLoader(lastFm, multiFactory.build(RemoteArtworkProvider::class.java, InputStream::class.java) as RemoteArtworkModelLoader)
        }

        override fun teardown() {

        }
    }


    class LastFmAlbumArtistRemoteArtworkProvider(
        private val lastFm: LastFmService.LastFm,
        private val albumArtist: AlbumArtist
    ) : AlbumArtistArtworkProvider(albumArtist),
        RemoteArtworkProvider {

        override fun getArtworkUri(): Call<out ArtworkUrlResult> {
            return lastFm.getLastFmArtist(albumArtist.name)
        }
    }
}