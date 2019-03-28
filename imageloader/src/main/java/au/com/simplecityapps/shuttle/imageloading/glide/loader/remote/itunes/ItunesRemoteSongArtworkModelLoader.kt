package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.itunes

import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.itunes.ItunesService
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import java.io.InputStream

class ItunesRemoteSongArtworkModelLoader(
    private val itunes: ItunesService.Itunes,
    private val remoteArtworkModelLoader: RemoteArtworkModelLoader
) : ModelLoader<Song, InputStream> {

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        val album = Album(-1, model.albumName, model.albumArtistId, model.albumArtistName, 0, 0, 0)
        return remoteArtworkModelLoader.buildLoadData(ItunesRemoteAlbumArtworkModelLoader.ItunesAlbumRemoteArtworkProvider(itunes, album), width, height, options)
    }

    override fun handles(model: Song): Boolean {
        return true
    }


    class Factory(
        private val itunes: ItunesService.Itunes
    ) : ModelLoaderFactory<Song, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
            return ItunesRemoteSongArtworkModelLoader(itunes, multiFactory.build(RemoteArtworkProvider::class.java, InputStream::class.java) as RemoteArtworkModelLoader)
        }

        override fun teardown() {

        }
    }
}