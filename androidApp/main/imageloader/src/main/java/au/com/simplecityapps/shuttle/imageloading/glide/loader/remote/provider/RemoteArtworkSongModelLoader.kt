package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.provider

import au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote.RemoteArtworkSongFetcher
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.SongArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import kotlinx.coroutines.CoroutineScope
import java.io.InputStream

class RemoteArtworkSongModelLoader(
    private val preferenceManager: GeneralPreferenceManager,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val coroutineScope: CoroutineScope
) : ModelLoader<Song, InputStream> {

    override fun handles(model: Song): Boolean {
        return true
    }

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        if (preferenceManager.artworkLocalOnly) {
            return null
        }

        return ModelLoader.LoadData(ObjectKey(SongArtworkProvider(model).getCacheKey()), RemoteArtworkSongFetcher(model, remoteArtworkProvider, coroutineScope))
    }

    class Factory(
        private val preferenceManager: GeneralPreferenceManager,
        private val remoteArtworkProvider: RemoteArtworkProvider,
        private val coroutineScope: CoroutineScope
    ) : ModelLoaderFactory<Song, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
            return RemoteArtworkSongModelLoader(preferenceManager, remoteArtworkProvider, coroutineScope)
        }

        override fun teardown() {}
    }
}
