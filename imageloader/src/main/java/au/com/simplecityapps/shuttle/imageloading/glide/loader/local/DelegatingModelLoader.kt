package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import java.io.InputStream

enum class ArtworkLocation {
    Directory, Tags
}

data class WrappedArtworkModel(val model: Any, val location: ArtworkLocation)

class DelegatingModelLoader(
    private val directoryAlbumArtistLocalArtworkModelLoader: DirectoryAlbumArtistLocalArtworkModelLoader,
    private val directoryAlbumLocalArtworkModelLoader: DirectoryAlbumLocalArtworkModelLoader,
    private val directorySongLocalArtworkModelLoader: DirectorySongLocalArtworkModelLoader,
    private val tagLibAlbumLocalArtworkModelLoader: TagLibAlbumLocalArtworkModelLoader,
    private val tagLibSongLocalArtworkModelLoader: TagLibSongLocalArtworkModelLoader
) : ModelLoader<WrappedArtworkModel, InputStream> {

    class Factory(
        private val directoryAlbumArtistLocalArtworkModelLoaderFactory: DirectoryAlbumArtistLocalArtworkModelLoader.Factory,
        private val directoryAlbumLocalArtworkModelLoaderFactory: DirectoryAlbumLocalArtworkModelLoader.Factory,
        private val directorySongLocalArtworkModelLoaderFactory: DirectorySongLocalArtworkModelLoader.Factory,
        private val tagLibAlbumLocalArtworkModelLoaderFactory: TagLibAlbumLocalArtworkModelLoader.Factory,
        private val tagLibSongLocalArtworkModelLoaderFactory: TagLibSongLocalArtworkModelLoader.Factory
    ) : ModelLoaderFactory<WrappedArtworkModel, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<WrappedArtworkModel, InputStream> {
            return DelegatingModelLoader(
                directoryAlbumArtistLocalArtworkModelLoaderFactory.build(multiFactory) as DirectoryAlbumArtistLocalArtworkModelLoader,
                directoryAlbumLocalArtworkModelLoaderFactory.build(multiFactory) as DirectoryAlbumLocalArtworkModelLoader,
                directorySongLocalArtworkModelLoaderFactory.build(multiFactory) as DirectorySongLocalArtworkModelLoader,
                tagLibAlbumLocalArtworkModelLoaderFactory.build(multiFactory) as TagLibAlbumLocalArtworkModelLoader,
                tagLibSongLocalArtworkModelLoaderFactory.build(multiFactory) as TagLibSongLocalArtworkModelLoader
            )
        }

        override fun teardown() {
        }
    }


    override fun buildLoadData(wrappedModel: WrappedArtworkModel, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return when (wrappedModel.location) {
            ArtworkLocation.Directory -> {
                when (wrappedModel.model) {
                    is AlbumArtist -> directoryAlbumArtistLocalArtworkModelLoader.buildLoadData(wrappedModel.model, width, height, options)
                    is Album -> directoryAlbumLocalArtworkModelLoader.buildLoadData(wrappedModel.model, width, height, options)
                    is Song -> directorySongLocalArtworkModelLoader.buildLoadData(wrappedModel.model, width, height, options)
                    else -> null
                }
            }
            ArtworkLocation.Tags -> {
                when (wrappedModel.model) {
                    is Album -> tagLibAlbumLocalArtworkModelLoader.buildLoadData(wrappedModel.model, width, height, options)
                    is Song -> tagLibSongLocalArtworkModelLoader.buildLoadData(wrappedModel.model, width, height, options)
                    else -> null
                }
            }
        }
    }

    override fun handles(wrappedModel: WrappedArtworkModel): Boolean {
        return when (wrappedModel.location) {
            ArtworkLocation.Directory -> {
                when (wrappedModel.model) {
                    is AlbumArtist -> true
                    is Album -> true
                    is Song -> true
                    else -> false
                }
            }
            ArtworkLocation.Tags -> {
                when (wrappedModel.model) {
                    is AlbumArtist -> false
                    is Album -> true
                    is Song -> true
                    else -> false
                }
            }
        }
    }
}