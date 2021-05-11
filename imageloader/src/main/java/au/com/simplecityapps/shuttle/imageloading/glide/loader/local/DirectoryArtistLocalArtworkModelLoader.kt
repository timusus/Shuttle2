package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtistArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

class DirectoryAlbumArtistLocalArtworkModelLoader(
    private val context: Context,
    private val localArtworkModelLoader: LocalArtworkModelLoader,
    private val songRepository: SongRepository
) : ModelLoader<AlbumArtist, InputStream> {

    override fun buildLoadData(model: AlbumArtist, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return localArtworkModelLoader.buildLoadData(DirectoryAlbumArtistLocalArtworkProvider(context, model, songRepository), width, height, options)
    }

    override fun handles(model: AlbumArtist): Boolean {
        return true
    }


    class Factory(
        private val context: Context,
        private val songRepository: SongRepository
    ) : ModelLoaderFactory<AlbumArtist, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtist, InputStream> {
            return DirectoryAlbumArtistLocalArtworkModelLoader(context, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader, songRepository)
        }

        override fun teardown() {
        }
    }


    class DirectoryAlbumArtistLocalArtworkProvider(
        private val context: Context,
        private val albumArtist: AlbumArtist,
        private val songRepository: SongRepository
    ) : AlbumArtistArtworkProvider(albumArtist),
        LocalArtworkProvider {

        override fun getInputStream(): InputStream? {
            return runBlocking {
                songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(albumArtist.groupKey))))
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
            private val pattern by lazy { Pattern.compile("artist.*\\.(jpg|jpeg|png)", Pattern.CASE_INSENSITIVE) }
        }
    }
}