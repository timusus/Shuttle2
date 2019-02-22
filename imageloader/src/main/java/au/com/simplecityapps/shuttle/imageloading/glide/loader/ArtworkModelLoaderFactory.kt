package au.com.simplecityapps.shuttle.imageloading.glide.loader

import au.com.simplecityapps.shuttle.imageloading.glide.provider.ArtworkProvider
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import okhttp3.OkHttpClient
import java.io.InputStream

class ArtworkModelLoaderFactory(
    private val okHttpClient: OkHttpClient
) : ModelLoaderFactory<ArtworkProvider, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<ArtworkProvider, InputStream> {
        return ArtworkModelLoader(okHttpClient)
    }

    override fun teardown() {
    }

}