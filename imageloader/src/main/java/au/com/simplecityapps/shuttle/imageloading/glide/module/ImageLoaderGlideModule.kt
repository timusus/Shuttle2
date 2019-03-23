package au.com.simplecityapps.shuttle.imageloading.glide.module

import android.content.Context
import android.util.Log
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.DiskSongLocalArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.TagLibSongLocalArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm.LastFmRemoteAlbumArtistArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm.LastFmRemoteAlbumArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm.LastFmRemoteSongArtworkModelLoaderFactory
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

        val songRepository = (context.applicationContext as CoreComponentProvider).provideCoreComponent().getSongRepository()

        // Generic loaders

        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))

        registry.append(LocalArtworkProvider::class.java, InputStream::class.java, LocalArtworkModelLoaderFactory())
        registry.append(RemoteArtworkProvider::class.java, InputStream::class.java, RemoteArtworkModelLoaderFactory(okHttpClient))


        // Local

        registry.append(Song::class.java, InputStream::class.java, DiskSongLocalArtworkModelLoaderFactory())
        registry.append(Song::class.java, InputStream::class.java, TagLibSongLocalArtworkModelLoaderFactory(artworkProvider))


        // Remote

        val lastFm = LastFmService(okHttpClient).lastFm
        registry.append(Song::class.java, InputStream::class.java, LastFmRemoteSongArtworkModelLoaderFactory(lastFm))
        registry.append(Album::class.java, InputStream::class.java, LastFmRemoteAlbumArtworkModelLoaderFactory(lastFm))
        registry.append(AlbumArtist::class.java, InputStream::class.java, LastFmRemoteAlbumArtistArtworkModelLoaderFactory(lastFm))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)

        builder.setLogLevel(Log.INFO)
    }
}