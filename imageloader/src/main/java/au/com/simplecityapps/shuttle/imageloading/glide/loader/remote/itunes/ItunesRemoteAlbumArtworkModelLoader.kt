package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.itunes

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.itunes.ItunesService
import au.com.simplecityapps.shuttle.imageloading.networking.itunes.getItunesAlbum
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Album
import retrofit2.Call
import java.io.InputStream

class ItunesRemoteAlbumArtworkModelLoader(
    private val itunes: ItunesService.Itunes,
    private val remoteArtworkModelLoader: RemoteArtworkModelLoader
) : ModelLoader<Album, InputStream> {

    override fun buildLoadData(model: Album, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return remoteArtworkModelLoader.buildLoadData(ItunesAlbumRemoteArtworkProvider(itunes, model), width, height, options)
    }

    override fun handles(model: Album): Boolean {
        return true
    }


    class Factory(
        private val lastFm: ItunesService.Itunes
    ) : ModelLoaderFactory<Album, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> {
            return ItunesRemoteAlbumArtworkModelLoader(lastFm, multiFactory.build(RemoteArtworkProvider::class.java, InputStream::class.java) as RemoteArtworkModelLoader)
        }

        override fun teardown() {

        }
    }


    class ItunesAlbumRemoteArtworkProvider(
        private val itunes: ItunesService.Itunes,
        private val album: Album
    ) : AlbumArtworkProvider(album),
        RemoteArtworkProvider {

        override fun getArtworkUri(): Call<out ArtworkUrlResult> {
            return itunes.getItunesAlbum(album)
        }
    }
}


