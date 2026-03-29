package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.SongArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.shuttle.model.Song
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

class DirectorySongLocalArtworkModelLoader(
    private val context: Context,
    private val localArtworkModelLoader: LocalArtworkModelLoader
) : ModelLoader<Song, InputStream> {
    override fun buildLoadData(
        model: Song,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? = localArtworkModelLoader.buildLoadData(DirectorySongLocalArtworkProvider(context, model), width, height, options)

    override fun handles(model: Song): Boolean = true

    class Factory(val context: Context) : ModelLoaderFactory<Song, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> = DirectorySongLocalArtworkModelLoader(context, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader)

        override fun teardown() {
        }
    }

    class DirectorySongLocalArtworkProvider(
        private val context: Context,
        song: Song
    ) : SongArtworkProvider(song),
        LocalArtworkProvider {
        override fun getInputStream(): InputStream? {
            val parentDocumentFile =
                if (DocumentsContract.isDocumentUri(context, song.path.toUri())) {
                    val parent = song.path.substringBeforeLast("%2F", "")
                    if (parent.isNotEmpty()) {
                        DocumentFile.fromTreeUri(context, parent.toUri())
                    } else {
                        null
                    }
                } else {
                    File(song.path).parentFile?.let { parent ->
                        DocumentFile.fromFile(parent)
                    }
                }

            return parentDocumentFile?.listFiles()
                ?.filter {
                    it.type?.startsWith("image") == true &&
                        it.length() > 1024 &&
                        pattern.matcher(it.name ?: "").matches()
                }
                ?.maxByOrNull { it.length() }
                ?.let { documentFile ->
                    context.contentResolver.openInputStream(documentFile.uri)
                }
        }

        companion object {
            private val pattern by lazy { Pattern.compile("(\\.?(folder|cover|album|albumart|front|artwork)).*\\.(jpg|jpeg|png|webp)", Pattern.CASE_INSENSITIVE) }
        }
    }
}
