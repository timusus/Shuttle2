package au.com.simplecityapps.shuttle.imageloading.glide.module

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.util.Log
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.*
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.AlbumArtistArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.AlbumArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.SongArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.palette.ColorSetTranscoder
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.simplecity.amp_library.glide.palette.ColorSet
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepositoryProvider
import com.simplecityapps.shuttle.dagger.GeneralPreferenceManagerProvider
import com.simplecityapps.shuttle.dagger.OkHttpClientProvider
import java.io.IOException
import java.io.InputStream

object NoConnectivityException : IOException("No connectivity")

@GlideModule
class ImageLoaderGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val preferenceManager = (context.applicationContext as GeneralPreferenceManagerProvider).provideGeneralPreferenceManager()

        val songRepository = (context.applicationContext as SongRepositoryProvider).provideSongRepository()

        val okHttpClient = (context.applicationContext as OkHttpClientProvider)
            .provideOkHttpClient()
            .newBuilder()
            .addNetworkInterceptor { chain ->
                if (preferenceManager.artworkWifiOnly && connectivityManager?.isActiveNetworkMetered == true) {
                    throw NoConnectivityException
                }
                chain.proceed(chain.request())
            }
            .build()


        // Generic loaders

        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))

        registry.append(LocalArtworkProvider::class.java, InputStream::class.java, LocalArtworkModelLoader.Factory())

        registry.register(Bitmap::class.java, ColorSet::class.java, ColorSetTranscoder(context))


        // Local

        registry.append(Song::class.java, InputStream::class.java, DiskSongLocalArtworkModelLoader.Factory())
        registry.append(Song::class.java, InputStream::class.java, TagLibSongLocalArtworkModelLoader.Factory(context))
        registry.append(Album::class.java, InputStream::class.java, DelegatingAlbumLocalArtworkModelLoader.Factory(songRepository))


        // Remote

        registry.append(Song::class.java, InputStream::class.java, SongArtworkModelLoader.Factory(preferenceManager))
        registry.append(Album::class.java, InputStream::class.java, AlbumArtworkModelLoader.Factory(preferenceManager))
        registry.append(AlbumArtist::class.java, InputStream::class.java, AlbumArtistArtworkModelLoader.Factory(preferenceManager))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setLogLevel(Log.ERROR)
    }
}