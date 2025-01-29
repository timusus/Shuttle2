package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.Context
import android.net.Uri
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.AlbumArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.query.SongQuery
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class TagLibAlbumLocalArtworkModelLoader(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val localArtworkModelLoader: LocalArtworkModelLoader,
    private val songRepository: SongRepository
) : ModelLoader<Album, InputStream> {
    override fun buildLoadData(
        model: Album,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? = localArtworkModelLoader.buildLoadData(TagLibAlbumLocalArtworkProvider(context, kTagLib, model, songRepository), width, height, options)

    override fun handles(model: Album): Boolean = true

    class Factory(
        private val context: Context,
        private val kTagLib: KTagLib,
        private val songRepository: SongRepository
    ) : ModelLoaderFactory<Album, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> = TagLibAlbumLocalArtworkModelLoader(context, kTagLib, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader, songRepository)

        override fun teardown() {
        }
    }

    class TagLibAlbumLocalArtworkProvider(
        private val context: Context,
        private val kTagLib: KTagLib,
        private val album: Album,
        private val songRepository: SongRepository
    ) : AlbumArtworkProvider(album),
        LocalArtworkProvider {
        override fun getInputStream(): InputStream? = runBlocking {
            songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
                .firstOrNull()
                ?.firstOrNull()
                ?.let { song ->
                    val uri: Uri =
                        if (song.path.startsWith("content://")) {
                            Uri.parse(song.path)
                        } else {
                            Uri.fromFile(File(song.path))
                        }
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            kTagLib.getArtwork(pfd.detachFd())?.inputStream()
                        }
                    } catch (e: SecurityException) {
                        Timber.v("Failed to retrieve artwork (permission denial)")
                        null
                    } catch (e: IllegalStateException) {
                        Timber.v("Failed to retrieve artwork (fd problem)")
                        null
                    } catch (e: SecurityException) {
                        Timber.v("Failed to retrieve artwork (security problem)")
                        null
                    } catch (e: FileNotFoundException) {
                        Timber.v("Failed to retrieve artwork (file not found)")
                        null
                    }
                }
        }
    }
}
