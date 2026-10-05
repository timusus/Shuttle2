package com.simplecityapps.imageloading.di

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.imageloading.coil.AlbumArtistArtworkFetcher
import com.simplecityapps.imageloading.coil.AlbumArtistArtworkKeyer
import com.simplecityapps.imageloading.coil.AlbumArtworkKeyer
import com.simplecityapps.imageloading.coil.ArtistHeroArtworkKeyer
import com.simplecityapps.imageloading.coil.ArtworkFetcher
import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.imageloading.coil.CoilArtworkImageLoader
import com.simplecityapps.imageloading.coil.SongArtworkKeyer
import com.simplecityapps.imageloading.coil.artworkCacheKey
import com.simplecityapps.imageloading.coil.on
import com.simplecityapps.imageloading.coil.source.ArtworkTagReader
import com.simplecityapps.imageloading.coil.source.EmbeddedAlbumArtworkSource
import com.simplecityapps.imageloading.coil.source.EmbeddedSongArtworkSource
import com.simplecityapps.imageloading.coil.source.FolderAlbumArtistArtworkSource
import com.simplecityapps.imageloading.coil.source.FolderAlbumArtworkSource
import com.simplecityapps.imageloading.coil.source.FolderSongArtworkSource
import com.simplecityapps.imageloading.coil.source.MediaServerAlbumArtistArtworkSource
import com.simplecityapps.imageloading.coil.source.MediaServerAlbumArtworkSource
import com.simplecityapps.imageloading.coil.source.MediaServerSongArtworkSource
import com.simplecityapps.imageloading.coil.source.MediaStoreAlbumArtworkSource
import com.simplecityapps.imageloading.coil.source.MediaStoreSongArtworkSource
import com.simplecityapps.imageloading.coil.source.S2AlbumArtistArtworkSource
import com.simplecityapps.imageloading.coil.source.S2AlbumArtworkSource
import com.simplecityapps.imageloading.coil.source.S2SongArtworkSource
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.RemoteArtworkInterceptor
import com.simplecityapps.mediaprovider.S2ArtworkApi
import com.simplecityapps.mediaprovider.TagReadGuard
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.ui.actions.LoadArtistArtwork
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

object NoConnectivityException : IOException("No connectivity")

@BindingContainer
@ContributesTo(AppScope::class)
interface RemoteArtworkInterceptorModule {
    // Empty unless a provider module contributes one
    @Multibinds(allowEmpty = true)
    @RemoteArtworkInterceptor
    fun remoteArtworkInterceptors(): Set<Interceptor>
}

@BindingContainer
@ContributesTo(AppScope::class)
object CoilModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        artworkSettings: ArtworkSettings,
        songRepository: SongRepository,
        kTagLib: KTagLib,
        tagReadGuard: TagReadGuard,
        remoteArtworkProvider: AggregateRemoteArtworkProvider,
        loadArtistArtwork: LoadArtistArtwork,
        @RemoteArtworkInterceptor remoteArtworkInterceptors: Set<@JvmSuppressWildcards Interceptor>
    ): ImageLoader {
        val artworkClient = artworkHttpClient(context, okHttpClient, artworkSettings, remoteArtworkInterceptors)

        // Android 13+ grants a music player only READ_MEDIA_AUDIO, so listing a shared storage folder leaves its images out. There
        // MediaProvider finds them instead: after embedded art, fall back to MediaStore's audio thumbnail, which is the folder
        // image when the song has no embedded art.
        val sharedStorageListsImages = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

        // Each model's sources, in the order they're tried
        val songSources =
            buildList<ArtworkSource<Song>> {
                add(FolderSongArtworkSource(context, sharedStorageListsImages))
                add(EmbeddedSongArtworkSource(context, ArtworkTagReader(kTagLib::getArtwork), tagReadGuard))
                if (!sharedStorageListsImages) add(MediaStoreSongArtworkSource(context))
                add(MediaServerSongArtworkSource(artworkSettings, remoteArtworkProvider))
                add(S2SongArtworkSource(artworkSettings))
            }
        val albumSources =
            buildList<ArtworkSource<Album>> {
                add(FolderAlbumArtworkSource(context, songRepository, sharedStorageListsImages))
                add(EmbeddedAlbumArtworkSource(context, ArtworkTagReader(kTagLib::getArtwork), tagReadGuard, songRepository))
                if (!sharedStorageListsImages) add(MediaStoreAlbumArtworkSource(context, songRepository))
                add(MediaServerAlbumArtworkSource(artworkSettings, songRepository, remoteArtworkProvider))
                add(S2AlbumArtworkSource(artworkSettings))
            }
        // An artist's image (#781, #823), on their page's hero and their rows alike: their own image, the online one only when
        // their tags pin them down, else their top album's cover. An artist image under the rule's minimum size counts as
        // absent. MediaStore's artist "image" is an album thumbnail, which the fallback album covers properly.
        val artistHeroSources =
            buildList<ArtworkSource<ArtistHeroArtwork>> {
                val minimumSize = ArtistHeroArtwork.MIN_ARTIST_IMAGE_SIZE
                add(FolderAlbumArtistArtworkSource(context, songRepository, sharedStorageListsImages).on(minimumSize) { it.artist })
                add(MediaServerAlbumArtistArtworkSource(artworkSettings, songRepository, remoteArtworkProvider).on(minimumSize) { it.artist })
                add(S2AlbumArtistArtworkSource(artworkSettings).on(minimumSize) { hero -> hero.artist.takeIf { hero.onlineLookup } })
                addAll(albumSources.map { source -> source.on { it.fallbackAlbum } })
            }
        val artistHeroFetcher = ArtworkFetcher.Factory(ArtistHeroArtwork::artworkCacheKey, artistHeroSources)

        return ImageLoader.Builder(context)
            // Every component is registered here, so a stray library can't add fetchers or decoders behind our back
            .serviceLoaderEnabled(false)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { artworkClient }))
                add(SongArtworkKeyer)
                add(AlbumArtworkKeyer)
                add(AlbumArtistArtworkKeyer)
                add(ArtistHeroArtworkKeyer)
                add(ArtworkFetcher.Factory(Song::artworkCacheKey, songSources))
                add(ArtworkFetcher.Factory(Album::artworkCacheKey, albumSources))
                add(AlbumArtistArtworkFetcher.Factory(loadArtistArtwork::invoke, artistHeroFetcher))
                add(artistHeroFetcher)
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

    @Provides
    fun provideArtworkImageLoader(imageLoader: CoilArtworkImageLoader): ArtworkImageLoader = imageLoader

    /**
     * The app client, plus the S2 artwork API's credentials and its wifi-only rule. The rule covers only the S2 API, as it always
     * has: media server artwork comes from the server the user is already streaming from.
     *
     * Media servers that need credentials for artwork get them from [remoteArtworkInterceptors], which run as network interceptors so each hop of
     * a redirect is checked against the server they belong to.
     */
    private fun artworkHttpClient(
        context: Context,
        okHttpClient: OkHttpClient,
        artworkSettings: ArtworkSettings,
        remoteArtworkInterceptors: Set<Interceptor>
    ): OkHttpClient {
        val connectivityManager: ConnectivityManager? = context.getSystemService()
        return okHttpClient
            .newBuilder()
            .apply { remoteArtworkInterceptors.forEach(::addNetworkInterceptor) }
            .authenticator { route, response ->
                if (route?.address?.url?.host == S2ArtworkApi.HOST && response.request.header("Authorization") == null) {
                    response.request
                        .newBuilder()
                        .header("Authorization", Credentials.basic(S2ArtworkApi.USERNAME, S2ArtworkApi.PASSWORD))
                        .build()
                } else {
                    null
                }
            }
            .addNetworkInterceptor { chain ->
                if (chain.request().url.host == S2ArtworkApi.HOST && artworkSettings.wifiOnly.value && connectivityManager?.isActiveNetworkMetered == true) {
                    throw NoConnectivityException
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    private const val MEMORY_CACHE_PERCENT = 0.25
    private const val DISK_CACHE_DIRECTORY = "artwork"
    private const val DISK_CACHE_BYTES = 250L * 1024 * 1024
}
