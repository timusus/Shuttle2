package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

class DiskAlbumLocalArtworkModelLoader(
    private val localArtworkModelLoader: LocalArtworkModelLoader,
    private val songRepository: SongRepository
) : ModelLoader<Album, InputStream> {

    override fun buildLoadData(model: Album, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return localArtworkModelLoader.buildLoadData(DiskAlbumLocalArtworkProvider(model, songRepository), width, height, options)
    }

    override fun handles(model: Album): Boolean {
        return true
    }


    class Factory(private val songRepository: SongRepository) : ModelLoaderFactory<Album, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> {
            return DiskAlbumLocalArtworkModelLoader(multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader, songRepository)
        }

        override fun teardown() {
        }
    }


    class DiskAlbumLocalArtworkProvider(
        private val album: Album,
        private val songRepository: SongRepository
    ) : AlbumArtworkProvider(album),
        LocalArtworkProvider {

        override fun getInputStream(): InputStream? {
            return runBlocking {
                songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
                    .firstOrNull()
                    ?.firstOrNull()
                    ?.let { song ->
                        if (song.path.startsWith("content://")) {
                            null
                        } else {
                            File(song.path).parentFile?.listFiles { file -> pattern.matcher(file.name).matches() }?.firstOrNull { it.length() > 1024 }?.inputStream()
                        }
                    }
            }
        }

        companion object {
            private val pattern by lazy { Pattern.compile("(folder|cover|album).*\\.(jpg|jpeg|png)", Pattern.CASE_INSENSITIVE) }
        }
    }
}