package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote

import au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.RemoteArtworkProvider
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import okhttp3.OkHttpClient
import java.io.InputStream

class RemoteArtworkModelLoaderFactory(
    private val okHttpClient: OkHttpClient
) : ModelLoaderFactory<RemoteArtworkProvider, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<RemoteArtworkProvider, InputStream> {
        return RemoteArtworkModelLoader(okHttpClient)
    }

    override fun teardown() {

    }
}