package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.SongArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Song
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

class DiskSongLocalArtworkModelLoader(
    private val localArtworkModelLoader: LocalArtworkModelLoader
) : ModelLoader<Song, InputStream> {

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return localArtworkModelLoader.buildLoadData(DiskSongLocalArtworkProvider(model), width, height, options)
    }

    override fun handles(model: Song): Boolean {
        return true
    }


    class DiskSongLocalArtworkProvider(
        private val song: Song
    ) : SongArtworkProvider(song),
        LocalArtworkProvider {

        override fun getInputStream(): InputStream? {
            val pattern = Pattern.compile("(folder|cover|album).*\\.(jpg|jpeg|png)", Pattern.CASE_INSENSITIVE)
            return File(song.path).parentFile.listFiles { file -> pattern.matcher(file.name).matches() }.firstOrNull { it.length() > 1024 }?.inputStream()
        }
    }
}


class DiskSongLocalArtworkModelLoaderFactory : ModelLoaderFactory<Song, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
        return DiskSongLocalArtworkModelLoader(multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader)
    }

    override fun teardown() {
    }
}