package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.SongArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.taglib.ArtworkProvider
import java.io.InputStream

class TagLibSongLocalArtworkModelLoader(
    private val tagLibArtworkProvider: ArtworkProvider,
    private val localArtworkModelLoader: LocalArtworkModelLoader
) : ModelLoader<Song, InputStream> {

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return localArtworkModelLoader.buildLoadData(TagLibSongLocalArtworkProvider(tagLibArtworkProvider, model), width, height, options)
    }

    override fun handles(model: Song): Boolean {
        return true
    }


    class Factory(
        private val tagLibArtworkProvider: ArtworkProvider
    ) : ModelLoaderFactory<Song, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
            return TagLibSongLocalArtworkModelLoader(tagLibArtworkProvider, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader)
        }

        override fun teardown() {

        }
    }


    class TagLibSongLocalArtworkProvider(
        private val tagLibArtworkProvider: ArtworkProvider,
        private val song: Song
    ) : SongArtworkProvider(song),
        LocalArtworkProvider {

        override fun getInputStream(): InputStream? {
            return tagLibArtworkProvider.getArtwork(song.path)?.inputStream()
        }
    }
}