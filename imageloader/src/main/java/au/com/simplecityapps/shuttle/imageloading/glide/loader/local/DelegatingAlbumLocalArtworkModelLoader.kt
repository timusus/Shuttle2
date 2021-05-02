package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.InputStream

class DelegatingAlbumLocalArtworkModelLoader(
    private val songRepository: SongRepository,
    private val songLoader: ModelLoader<Song, InputStream>
) : ModelLoader<Album, InputStream> {

    override fun buildLoadData(model: Album, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return runBlocking {
            songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(model.groupKey))))
                .firstOrNull()
                ?.firstOrNull()
                ?.let { song -> songLoader.buildLoadData(song, width, height, options) }
        }
    }

    override fun handles(model: Album): Boolean {
        return true
    }


    class Factory(
        private val songRepository: SongRepository
    ) : ModelLoaderFactory<Album, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> {
            return DelegatingAlbumLocalArtworkModelLoader(songRepository, multiFactory.build(Song::class.java, InputStream::class.java))
        }

        override fun teardown() {}
    }
}