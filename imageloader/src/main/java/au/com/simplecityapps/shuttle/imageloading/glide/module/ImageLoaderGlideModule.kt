package au.com.simplecityapps.shuttle.imageloading.glide.module

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.util.Log
import androidx.annotation.Keep
import au.com.simplecityapps.BuildConfig
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.*
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.AlbumArtistArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.AlbumArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.SongArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.palette.ColorSet
import au.com.simplecityapps.shuttle.imageloading.palette.ColorSetTranscoder
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.InputStream

object NoConnectivityException : IOException("No connectivity")

@GlideModule
@Excludes(OkHttpLibraryGlideModule::class)
@Keep
class ImageLoaderGlideModule : AppGlideModule() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ImageLoaderGlideModuleEntryPoint {
        fun provideHttpClient(): OkHttpClient
        fun providePreferenceManager(): GeneralPreferenceManager
        fun provideSongRepository(): SongRepository
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val entryPoint: ImageLoaderGlideModuleEntryPoint = EntryPointAccessors.fromApplication(context, ImageLoaderGlideModuleEntryPoint::class.java)

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val okHttpClient = entryPoint.provideHttpClient()
            .newBuilder()
            .authenticator { _, response ->
                response.request
                    .newBuilder()
                    .header("Authorization", Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"))
                    .build()
            }
            .addNetworkInterceptor { chain ->
                if (entryPoint.providePreferenceManager().artworkWifiOnly && connectivityManager?.isActiveNetworkMetered == true) {
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
        registry.append(Album::class.java, InputStream::class.java, DiskAlbumLocalArtworkModelLoader.Factory(entryPoint.provideSongRepository()))
        registry.append(Album::class.java, InputStream::class.java, TagLibAlbumLocalArtworkModelLoader.Factory(context, entryPoint.provideSongRepository()))

        // Remote
        registry.append(Song::class.java, InputStream::class.java, SongArtworkModelLoader.Factory(entryPoint.providePreferenceManager()))
        registry.append(Album::class.java, InputStream::class.java, AlbumArtworkModelLoader.Factory(entryPoint.providePreferenceManager()))
        registry.append(AlbumArtist::class.java, InputStream::class.java, AlbumArtistArtworkModelLoader.Factory(entryPoint.providePreferenceManager()))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        if (BuildConfig.DEBUG) {
            builder.setLogLevel(Log.ERROR)
        }
    }
}