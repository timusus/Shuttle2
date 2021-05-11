package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.provider

import au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote.RemoteArtworkAlbumFetcher
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlinx.coroutines.CoroutineScope
import java.io.InputStream

class RemoteArtworkAlbumModelLoader(
    private val preferenceManager: GeneralPreferenceManager,
    private val songRepository: SongRepository,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val coroutineScope: CoroutineScope
) : ModelLoader<Album, InputStream> {

    override fun handles(model: Album): Boolean {
        return true
    }

    override fun buildLoadData(model: Album, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        if (preferenceManager.artworkLocalOnly) {
            return null
        }

        return ModelLoader.LoadData(ObjectKey(AlbumArtworkProvider(model).getCacheKey()), RemoteArtworkAlbumFetcher(model, songRepository, remoteArtworkProvider, coroutineScope))
    }


    class Factory(
        private val preferenceManager: GeneralPreferenceManager,
        private val songRepository: SongRepository,
        private val remoteArtworkProvider: RemoteArtworkProvider,
        private val coroutineScope: CoroutineScope
    ) : ModelLoaderFactory<Album, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> {
            return RemoteArtworkAlbumModelLoader(preferenceManager, songRepository, remoteArtworkProvider, coroutineScope)
        }

        override fun teardown() {}
    }
}