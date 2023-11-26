package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.provider

import au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote.RemoteArtworkAlbumArtistFetcher
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtistArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope

class RemoteArtworkAlbumArtistModelLoader(
    private val preferenceManager: GeneralPreferenceManager,
    private val songRepository: SongRepository,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val coroutineScope: CoroutineScope
) : ModelLoader<AlbumArtist, InputStream> {
    override fun handles(model: AlbumArtist): Boolean {
        return true
    }

    override fun buildLoadData(
        model: AlbumArtist,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        if (preferenceManager.artworkLocalOnly) {
            return null
        }

        return ModelLoader.LoadData(ObjectKey(AlbumArtistArtworkProvider(model).getCacheKey()), RemoteArtworkAlbumArtistFetcher(model, songRepository, remoteArtworkProvider, coroutineScope))
    }

    class Factory(
        private val preferenceManager: GeneralPreferenceManager,
        private val songRepository: SongRepository,
        private val remoteArtworkProvider: RemoteArtworkProvider,
        private val coroutineScope: CoroutineScope
    ) : ModelLoaderFactory<AlbumArtist, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtist, InputStream> {
            return RemoteArtworkAlbumArtistModelLoader(preferenceManager, songRepository, remoteArtworkProvider, coroutineScope)
        }

        override fun teardown() {}
    }
}
