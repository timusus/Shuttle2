package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.Context
import android.net.Uri
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.SongArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.taglib.ArtworkProvider
import java.io.File
import java.io.InputStream

class TagLibSongLocalArtworkModelLoader(
    private val context: Context,
    private val tagLibArtworkProvider: ArtworkProvider,
    private val localArtworkModelLoader: LocalArtworkModelLoader
) : ModelLoader<Song, InputStream> {

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return localArtworkModelLoader.buildLoadData(TagLibSongLocalArtworkProvider(context, tagLibArtworkProvider, model), width, height, options)
    }

    override fun handles(model: Song): Boolean {
        return true
    }


    class Factory(
        private val context: Context,
        private val tagLibArtworkProvider: ArtworkProvider
    ) : ModelLoaderFactory<Song, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
            return TagLibSongLocalArtworkModelLoader(context, tagLibArtworkProvider, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader)
        }

        override fun teardown() {

        }
    }


    class TagLibSongLocalArtworkProvider(
        private val context: Context,
        private val tagLibArtworkProvider: ArtworkProvider,
        private val song: Song
    ) : SongArtworkProvider(song),
        LocalArtworkProvider {

        override fun getInputStream(): InputStream? {
            val uri: Uri = if (song.path.startsWith("content://")) {
                Uri.parse(song.path)
            } else {
                Uri.fromFile(File(song.path))
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                return tagLibArtworkProvider.getArtwork(pfd.fd)?.inputStream()
            }

            return null
        }
    }
}