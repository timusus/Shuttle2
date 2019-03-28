package au.com.simplecityapps.shuttle.imageloading.glide.module

import android.content.Context
import android.util.Log
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.DiskSongLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.TagLibSongLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.itunes.ItunesRemoteAlbumArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.itunes.ItunesRemoteSongArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm.LastFmRemoteAlbumArtistArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm.LastFmRemoteAlbumArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm.LastFmRemoteSongArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.networking.itunes.ItunesService
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.dagger.CoreComponentProvider
import java.io.InputStream

@GlideModule
class ImageLoaderGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {

        val okHttpClient = (context.applicationContext as CoreComponentProvider).provideCoreComponent().getOkHttpClient()

        val artworkProvider = (context.applicationContext as CoreComponentProvider).provideCoreComponent().getArtworkProvider()

        val gsonConverterFactory = (context.applicationContext as CoreComponentProvider).provideCoreComponent().getGsonConverterFactory()


        // Generic loaders

        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))

        registry.append(LocalArtworkProvider::class.java, InputStream::class.java, LocalArtworkModelLoader.Factory())
        registry.append(RemoteArtworkProvider::class.java, InputStream::class.java, RemoteArtworkModelLoader.Factory(okHttpClient))


        // Local

        registry.append(Song::class.java, InputStream::class.java, DiskSongLocalArtworkModelLoader.Factory())
        registry.append(Song::class.java, InputStream::class.java, TagLibSongLocalArtworkModelLoader.Factory(artworkProvider))


        // Remote

        val lastFm = LastFmService(okHttpClient, gsonConverterFactory).lastFm
        registry.append(Song::class.java, InputStream::class.java, LastFmRemoteSongArtworkModelLoader.Factory(lastFm))
        registry.append(Album::class.java, InputStream::class.java, LastFmRemoteAlbumArtworkModelLoader.Factory(lastFm))
        registry.append(AlbumArtist::class.java, InputStream::class.java, LastFmRemoteAlbumArtistArtworkModelLoader.Factory(lastFm))

        val itunes = ItunesService(okHttpClient, gsonConverterFactory).itunes
        registry.append(Song::class.java, InputStream::class.java, ItunesRemoteSongArtworkModelLoader.Factory(itunes))
        registry.append(Album::class.java, InputStream::class.java, ItunesRemoteAlbumArtworkModelLoader.Factory(itunes))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)

        builder.setLogLevel(Log.INFO)
    }
}