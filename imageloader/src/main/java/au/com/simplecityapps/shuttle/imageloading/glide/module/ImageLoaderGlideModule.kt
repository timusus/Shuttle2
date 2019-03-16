package au.com.simplecityapps.shuttle.imageloading.glide.module

import android.content.Context
import android.util.Log
import au.com.simplecityapps.shuttle.imageloading.glide.loader.local.LocalArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoaderFactory
import au.com.simplecityapps.shuttle.imageloading.glide.provider.local.LocalArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.RemoteArtworkProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.simplecityapps.shuttle.dagger.CoreComponentProvider
import java.io.InputStream

@GlideModule
class ImageLoaderGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {

        val okHttpClient = (context.applicationContext as CoreComponentProvider).provideCoreComponent().getOkHttpClient()

        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))

        registry.append(RemoteArtworkProvider::class.java, InputStream::class.java, RemoteArtworkModelLoaderFactory(okHttpClient))
        registry.append(LocalArtworkProvider::class.java, InputStream::class.java, LocalArtworkModelLoaderFactory())
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)

        builder.setLogLevel(Log.INFO)
    }
}