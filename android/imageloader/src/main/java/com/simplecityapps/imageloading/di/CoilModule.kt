package com.simplecityapps.imageloading.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import com.simplecityapps.imageloading.coil.AlbumArtistArtworkKeyer
import com.simplecityapps.imageloading.coil.AlbumArtworkKeyer
import com.simplecityapps.imageloading.coil.ArtworkFetcher
import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.imageloading.coil.SongArtworkKeyer
import com.simplecityapps.imageloading.coil.artworkCacheKey
import com.simplecityapps.imageloading.coil.source.EmbeddedAlbumArtworkSource
import com.simplecityapps.imageloading.coil.source.EmbeddedSongArtworkSource
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Singleton
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

object NoConnectivityException : IOException("No connectivity")

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        artworkSettings: ArtworkSettings,
        songRepository: SongRepository,
        kTagLib: KTagLib
    ): ImageLoader {
        val artworkClient = artworkHttpClient(context, okHttpClient, artworkSettings)

        // Each model's sources, in the order they're tried
        val songSources =
            listOf<ArtworkSource<Song>>(
                EmbeddedSongArtworkSource(context, kTagLib)
            )
        val albumSources =
            listOf<ArtworkSource<Album>>(
                EmbeddedAlbumArtworkSource(context, kTagLib, songRepository)
            )
        val albumArtistSources = listOf<ArtworkSource<AlbumArtist>>()

        return ImageLoader.Builder(context)
            // Every component is registered here, so a stray library can't add fetchers or decoders behind our back
            .serviceLoaderEnabled(false)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { artworkClient }))
                add(SongArtworkKeyer)
                add(AlbumArtworkKeyer)
                add(AlbumArtistArtworkKeyer)
                add(ArtworkFetcher.Factory(Song::artworkCacheKey, songSources))
                add(ArtworkFetcher.Factory(Album::artworkCacheKey, albumSources))
                add(ArtworkFetcher.Factory(AlbumArtist::artworkCacheKey, albumArtistSources))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(DISK_CACHE_DIRECTORY).toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .build()
    }

    /**
     * The app client, plus the S2 artwork API's credentials and its wifi-only rule. The rule covers only the S2 API, as it did
     * under Glide: media server artwork comes from the server the user is already streaming from.
     */
    private fun artworkHttpClient(
        context: Context,
        okHttpClient: OkHttpClient,
        artworkSettings: ArtworkSettings
    ): OkHttpClient {
        val connectivityManager: ConnectivityManager? = context.getSystemService()
        return okHttpClient
            .newBuilder()
            .authenticator { route, response ->
                if (route?.address?.url?.host == S2_ARTWORK_HOST && response.request.header("Authorization") == null) {
                    response.request
                        .newBuilder()
                        .header("Authorization", Credentials.basic("s2", "aEqRKgkCbqALjEm9Eg7e7Qi5"))
                        .build()
                } else {
                    null
                }
            }
            .addNetworkInterceptor { chain ->
                if (chain.request().url.host == S2_ARTWORK_HOST && artworkSettings.wifiOnly.value && connectivityManager?.isActiveNetworkMetered == true) {
                    throw NoConnectivityException
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    const val S2_ARTWORK_HOST = "api.shuttlemusicplayer.app"
    private const val MEMORY_CACHE_PERCENT = 0.25
    private const val DISK_CACHE_DIRECTORY = "artwork"
    private const val DISK_CACHE_BYTES = 250L * 1024 * 1024
}
