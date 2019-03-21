package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Album
import retrofit2.Call
import java.io.InputStream

class LastFmRemoteAlbumArtworkModelLoader(
    private val lastFm: LastFmService.LastFm,
    private val remoteArtworkModelLoader: RemoteArtworkModelLoader
) : ModelLoader<Album, InputStream> {

    override fun buildLoadData(model: Album, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return remoteArtworkModelLoader.buildLoadData(LastFmAlbumRemoteArtworkProvider(lastFm, model), width, height, options)
    }

    override fun handles(model: Album): Boolean {
        return true
    }


    class LastFmAlbumRemoteArtworkProvider(
        private val lastFm: LastFmService.LastFm,
        private val album: Album
    ) : AlbumArtworkProvider(album),
        RemoteArtworkProvider {

        override fun getArtworkUri(): Call<out ArtworkUrlResult> {
            return lastFm.getLastFmAlbum(album.albumArtistName, album.name)
        }
    }
}


class LastFmRemoteAlbumArtworkModelLoaderFactory(
    private val lastFm: LastFmService.LastFm
) : ModelLoaderFactory<Album, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> {
        return LastFmRemoteAlbumArtworkModelLoader(lastFm, multiFactory.build(RemoteArtworkProvider::class.java, InputStream::class.java) as RemoteArtworkModelLoader)
    }

    override fun teardown() {

    }
}