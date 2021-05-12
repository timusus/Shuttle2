package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.Context
import android.net.Uri
import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.SongArtworkProvider
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.model.Song
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class TagLibSongLocalArtworkModelLoader(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val localArtworkModelLoader: LocalArtworkModelLoader
) : ModelLoader<Song, InputStream> {

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return localArtworkModelLoader.buildLoadData(TagLibSongLocalArtworkProvider(context, kTagLib, model), width, height, options)
    }

    override fun handles(model: Song): Boolean {
        return true
    }


    class Factory(
        private val context: Context,
        private val kTagLib: KTagLib
    ) : ModelLoaderFactory<Song, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
            return TagLibSongLocalArtworkModelLoader(context, kTagLib, multiFactory.build(LocalArtworkProvider::class.java, InputStream::class.java) as LocalArtworkModelLoader)
        }

        override fun teardown() {

        }
    }


    class TagLibSongLocalArtworkProvider(
        private val context: Context,
        private val kTagLib: KTagLib,
        song: Song
    ) : SongArtworkProvider(song),
        LocalArtworkProvider {

        override fun getInputStream(): InputStream? {
            val uri: Uri = if (song.path.startsWith("content://")) {
                Uri.parse(song.path)
            } else {
                Uri.fromFile(File(song.path))
            }
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    return kTagLib.getArtwork(pfd.detachFd())?.inputStream()
                }
            } catch (e: SecurityException) {
                Timber.v("Failed to retrieve artwork (permission denial)")
                return null
            } catch (e: IllegalStateException) {
                Timber.v("Failed to retrieve artwork (fd problem)")
                return null
            } catch (e: SecurityException) {
                Timber.v("Failed to retrieve artwork (security problem)")
                return null
            } catch (e: FileNotFoundException) {
                Timber.v("Failed to retrieve artwork (file not found)")
                return null
            }

            return null
        }
    }
}