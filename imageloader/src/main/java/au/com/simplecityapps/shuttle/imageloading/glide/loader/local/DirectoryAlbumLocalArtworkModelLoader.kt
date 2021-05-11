package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
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

class DirectoryAlbumLocalArtworkModelLoader(
    private val context: Context,
    private val localArtworkModelLoader: LocalArtworkModelLoader,
    private val songRepository: SongRepository
) : ModelLoader<Album, InputStream> {

    override fun buildLoadData(model: Album, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream> {
        return localArtworkModelLoader.buildLoadData(DirectoryAlbumLocalArtworkProvider(context, model, songRepository), width, height, options)
    }

    override fun handles(model: Album): Boolean {
        return true
    }


    class Factory(
        private val context: Context,
        private val songRepository: SongRepository
    ) : ModelLoaderFactory<Album, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> {
            return DirectoryAlbumLocalArtworkModelLoader(context, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader, songRepository)
        }

        override fun teardown() {
        }
    }


    class DirectoryAlbumLocalArtworkProvider(
        private val context: Context,
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
                        val parentDocumentFile = if (DocumentsContract.isDocumentUri(context, song.path.toUri())) {
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

                        parentDocumentFile?.listFiles()
                            ?.filter {
                                it.type?.startsWith("image") == true
                                        && it.length() > 1024
                                        && pattern.matcher(it.name ?: "").matches()
                            }
                            ?.maxByOrNull { it.length() }
                            ?.let { documentFile ->
                                context.contentResolver.openInputStream(documentFile.uri)
                            }
                    }
            }
        }

        companion object {
            private val pattern by lazy { Pattern.compile("(folder|cover|album).*\\.(jpg|jpeg|png)", Pattern.CASE_INSENSITIVE) }
        }
    }
}