package au.com.simplecityapps.shuttle.imageloading.glide.module

import android.content.Context
import android.util.Log
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.DiskSongLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.TagLibSongLocalArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.AlbumArtistArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.AlbumArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork.SongArtworkModelLoader
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
import com.simplecityapps.taglib.ArtworkProvider
import okhttp3.OkHttpClient
import java.io.InputStream

@GlideModule
class ImageLoaderGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {

        val okHttpClient = OkHttpClient.Builder().build()

        // Generic loaders

        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))

        registry.append(LocalArtworkProvider::class.java, InputStream::class.java, LocalArtworkModelLoader.Factory())


        // Local

        registry.append(Song::class.java, InputStream::class.java, DiskSongLocalArtworkModelLoader.Factory())
        registry.append(Song::class.java, InputStream::class.java, TagLibSongLocalArtworkModelLoader.Factory(ArtworkProvider()))


        // Remote

        registry.append(Song::class.java, InputStream::class.java, SongArtworkModelLoader.Factory())
        registry.append(Album::class.java, InputStream::class.java, AlbumArtworkModelLoader.Factory())
        registry.append(AlbumArtist::class.java, InputStream::class.java, AlbumArtistArtworkModelLoader.Factory())
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setLogLevel(Log.ERROR)
    }
}