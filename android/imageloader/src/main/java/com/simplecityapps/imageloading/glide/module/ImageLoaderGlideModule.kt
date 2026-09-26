package com.simplecityapps.imageloading.glide.module

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.simplecityapps.imageloading.glide.loader.local.DirectoryAlbumArtistLocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.local.DirectoryAlbumLocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.local.DirectorySongLocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.local.LocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.local.LocalArtworkProvider
import com.simplecityapps.imageloading.glide.loader.local.MediaStoreAlbumLocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.local.MediaStoreSongLocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.local.TagLibAlbumLocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.local.TagLibSongLocalArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.remote.provider.RemoteArtworkAlbumArtistModelLoader
import com.simplecityapps.imageloading.glide.loader.remote.provider.RemoteArtworkAlbumModelLoader
import com.simplecityapps.imageloading.glide.loader.remote.provider.RemoteArtworkSongModelLoader
import com.simplecityapps.imageloading.glide.loader.remote.s2.S2AlbumArtistArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.remote.s2.S2AlbumArtworkModelLoader
import com.simplecityapps.imageloading.glide.loader.remote.s2.S2SongArtworkModelLoader
import com.simplecityapps.imageloading.palette.ColorSet
import com.simplecityapps.imageloading.palette.ColorSetTranscoder
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings
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

        fun provideArtworkSettings(): ArtworkSettings

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

        registerGenericLoaders(context, registry, entryPoint)
        registerLocalArtworkLoaders(context, registry, entryPoint)
        registerRemoteArtworkLoaders(registry, entryPoint)
        registerS2ArtworkLoaders(registry, entryPoint)
    }

    private fun registerGenericLoaders(
        context: Context,
        registry: Registry,
        entryPoint: ImageLoaderGlideModuleEntryPoint
    ) {
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(buildOkHttpClient(context, entryPoint)))
        registry.append(LocalArtworkProvider::class.java, InputStream::class.java, LocalArtworkModelLoader.Factory(entryPoint.provideCoroutineScope()))
        registry.register(Bitmap::class.java, ColorSet::class.java, ColorSetTranscoder(context))
    }

    private fun buildOkHttpClient(
        context: Context,
        entryPoint: ImageLoaderGlideModuleEntryPoint
    ): OkHttpClient {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        return entryPoint.provideHttpClient()
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
                if (entryPoint.provideArtworkSettings().wifiOnly.value && connectivityManager?.isActiveNetworkMetered == true) {
                    throw NoConnectivityException
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    private fun registerLocalArtworkLoaders(
        context: Context,
        registry: Registry,
        entryPoint: ImageLoaderGlideModuleEntryPoint
    ) {
        // Android 13+ grants a music player only READ_MEDIA_AUDIO, so listing a shared storage folder leaves its images out
        val sharedStorageListsImages = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

        registry.append(
            Song::class.java,
            InputStream::class.java,
            DirectorySongLocalArtworkModelLoader.Factory(
                context = context,
                sharedStorageListsImages = sharedStorageListsImages
            )
        )

        registry.append(
            Song::class.java,
            InputStream::class.java,
            TagLibSongLocalArtworkModelLoader.Factory(
                context = context,
                kTagLib = entryPoint.provideKTagLib()
            )
        )

        registry.append(
            Album::class.java,
            InputStream::class.java,
            DirectoryAlbumLocalArtworkModelLoader.Factory(
                context = context,
                songRepository = entryPoint.provideSongRepository(),
                sharedStorageListsImages = sharedStorageListsImages
            )
        )

        registry.append(
            AlbumArtist::class.java,
            InputStream::class.java,
            DirectoryAlbumArtistLocalArtworkModelLoader.Factory(
                context = context,
                songRepository = entryPoint.provideSongRepository(),
                sharedStorageListsImages = sharedStorageListsImages
            )
        )

        registry.append(
            Album::class.java,
            InputStream::class.java,
            TagLibAlbumLocalArtworkModelLoader.Factory(
                context = context,
                kTagLib = entryPoint.provideKTagLib(),
                songRepository = entryPoint.provideSongRepository()
            )
        )

        // Where the app can't list folder images, MediaProvider can: after embedded art (read at full size by TagLib), fall back
        // to MediaStore's audio thumbnail, which is the folder image when the song has no embedded art
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registry.append(
                Song::class.java,
                InputStream::class.java,
                MediaStoreSongLocalArtworkModelLoader.Factory(context = context)
            )
            registry.append(
                Album::class.java,
                InputStream::class.java,
                MediaStoreAlbumLocalArtworkModelLoader.Factory(
                    context = context,
                    songRepository = entryPoint.provideSongRepository()
                )
            )
        }
    }

    private fun registerRemoteArtworkLoaders(
        registry: Registry,
        entryPoint: ImageLoaderGlideModuleEntryPoint
    ) {
        registry.append(
            Song::class.java,
            InputStream::class.java,
            RemoteArtworkSongModelLoader.Factory(
                artworkSettings = entryPoint.provideArtworkSettings(),
                remoteArtworkProvider = entryPoint.provideAggregateRemoteArtworkProvider(),
                coroutineScope = entryPoint.provideCoroutineScope()
            )
        )
        registry.append(
            Album::class.java,
            InputStream::class.java,
            RemoteArtworkAlbumModelLoader.Factory(
                artworkSettings = entryPoint.provideArtworkSettings(),
                songRepository = entryPoint.provideSongRepository(),
                remoteArtworkProvider = entryPoint.provideAggregateRemoteArtworkProvider(),
                coroutineScope = entryPoint.provideCoroutineScope()
            )
        )
        registry.append(
            AlbumArtist::class.java,
            InputStream::class.java,
            RemoteArtworkAlbumArtistModelLoader.Factory(
                artworkSettings = entryPoint.provideArtworkSettings(),
                songRepository = entryPoint.provideSongRepository(),
                remoteArtworkProvider = entryPoint.provideAggregateRemoteArtworkProvider(),
                coroutineScope = entryPoint.provideCoroutineScope()
            )
        )
    }

    private fun registerS2ArtworkLoaders(
        registry: Registry,
        entryPoint: ImageLoaderGlideModuleEntryPoint
    ) {
        registry.append(
            Song::class.java,
            InputStream::class.java,
            S2SongArtworkModelLoader.Factory(
                artworkSettings = entryPoint.provideArtworkSettings()
            )
        )
        registry.append(
            Album::class.java,
            InputStream::class.java,
            S2AlbumArtworkModelLoader.Factory(
                artworkSettings = entryPoint.provideArtworkSettings()
            )
        )
        registry.append(
            AlbumArtist::class.java,
            InputStream::class.java,
            S2AlbumArtistArtworkModelLoader.Factory(
                artworkSettings = entryPoint.provideArtworkSettings()
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
