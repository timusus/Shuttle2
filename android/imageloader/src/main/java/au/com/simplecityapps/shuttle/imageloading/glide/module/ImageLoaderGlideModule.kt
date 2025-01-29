package au.com.simplecityapps.shuttle.imageloading.glide.module

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.util.Log
import androidx.annotation.Keep
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.DirectoryAlbumArtistLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.DirectoryAlbumLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.DirectorySongLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.TagLibAlbumLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.TagLibSongLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.provider.RemoteArtworkAlbumArtistModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.provider.RemoteArtworkAlbumModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.provider.RemoteArtworkSongModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.s2.S2AlbumArtistArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.s2.S2AlbumArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.s2.S2SongArtworkModelLoader
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
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.phrase.BuildConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import okhttp3.Credentials
import okhttp3.OkHttpClient

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

        fun provideAggregateRemoteArtworkProvider(): AggregateRemoteArtworkProvider

        fun provideKTagLib(): KTagLib

        @AppCoroutineScope
        fun provideCoroutineScope(): CoroutineScope
    }

    override fun registerComponents(
        context: Context,
        glide: Glide,
        registry: Registry
    ) {
        val entryPoint: ImageLoaderGlideModuleEntryPoint = EntryPointAccessors.fromApplication(context, ImageLoaderGlideModuleEntryPoint::class.java)

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val okHttpClient =
            entryPoint.provideHttpClient()
                .newBuilder()
                .authenticator { route, response ->
                    if (route?.address?.url?.host == "api.shuttlemusicplayer.app") {
                        response.request
                            .newBuilder()
                            .header("Authorization", Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"))
                            .build()
                    } else {
                        response.request
                    }
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

        val directorySongLocalArtworkModelLoaderFactory =
            DirectorySongLocalArtworkModelLoader.Factory(
                context = context
            )
        registry.append(
            Song::class.java,
            InputStream::class.java,
            directorySongLocalArtworkModelLoaderFactory
        )

        val tagLibSongLocalArtworkModelLoaderFactory =
            TagLibSongLocalArtworkModelLoader.Factory(
                context = context,
                kTagLib = entryPoint.provideKTagLib()
            )
        registry.append(
            Song::class.java,
            InputStream::class.java,
            tagLibSongLocalArtworkModelLoaderFactory
        )

        val directoryAlbumLocalArtworkModelLoaderFactory =
            DirectoryAlbumLocalArtworkModelLoader.Factory(
                context = context,
                songRepository = entryPoint.provideSongRepository()
            )
        registry.append(
            Album::class.java,
            InputStream::class.java,
            directoryAlbumLocalArtworkModelLoaderFactory
        )

        val directoryAlbumArtistLocalArtworkModelLoaderFactory =
            DirectoryAlbumArtistLocalArtworkModelLoader.Factory(
                context = context,
                songRepository = entryPoint.provideSongRepository()
            )
        registry.append(
            AlbumArtist::class.java,
            InputStream::class.java,
            directoryAlbumArtistLocalArtworkModelLoaderFactory
        )

        val tagLibAlbumLocalArtworkModelLoaderFactory =
            TagLibAlbumLocalArtworkModelLoader.Factory(
                context = context,
                kTagLib = entryPoint.provideKTagLib(),
                songRepository = entryPoint.provideSongRepository()
            )
        registry.append(
            Album::class.java,
            InputStream::class.java,
            tagLibAlbumLocalArtworkModelLoaderFactory
        )

        // Remote
        registry.append(
            Song::class.java,
            InputStream::class.java,
            RemoteArtworkSongModelLoader.Factory(
                preferenceManager = entryPoint.providePreferenceManager(),
                remoteArtworkProvider = entryPoint.provideAggregateRemoteArtworkProvider(),
                coroutineScope = entryPoint.provideCoroutineScope()
            )
        )
        registry.append(
            Album::class.java,
            InputStream::class.java,
            RemoteArtworkAlbumModelLoader.Factory(
                preferenceManager = entryPoint.providePreferenceManager(),
                songRepository = entryPoint.provideSongRepository(),
                remoteArtworkProvider = entryPoint.provideAggregateRemoteArtworkProvider(),
                coroutineScope = entryPoint.provideCoroutineScope()
            )
        )
        registry.append(
            AlbumArtist::class.java,
            InputStream::class.java,
            RemoteArtworkAlbumArtistModelLoader.Factory(
                preferenceManager = entryPoint.providePreferenceManager(),
                songRepository = entryPoint.provideSongRepository(),
                remoteArtworkProvider = entryPoint.provideAggregateRemoteArtworkProvider(),
                coroutineScope = entryPoint.provideCoroutineScope()
            )
        )

        // S2
        registry.append(
            Song::class.java,
            InputStream::class.java,
            S2SongArtworkModelLoader.Factory(
                preferenceManager = entryPoint.providePreferenceManager()
            )
        )
        registry.append(
            Album::class.java,
            InputStream::class.java,
            S2AlbumArtworkModelLoader.Factory(
                preferenceManager = entryPoint.providePreferenceManager()
            )
        )
        registry.append(
            AlbumArtist::class.java,
            InputStream::class.java,
            S2AlbumArtistArtworkModelLoader.Factory(
                preferenceManager = entryPoint.providePreferenceManager()
            )
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false

    override fun applyOptions(
        context: Context,
        builder: GlideBuilder
    ) {
        if (BuildConfig.DEBUG) {
            builder.setLogLevel(Log.ERROR)
        }
    }
}
