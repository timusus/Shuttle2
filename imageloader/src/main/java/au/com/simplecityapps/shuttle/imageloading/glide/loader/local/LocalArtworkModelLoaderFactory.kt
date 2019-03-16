package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import au.com.simplecityapps.shuttle.imageloading.glide.provider.local.LocalArtworkProvider
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import java.io.InputStream

class LocalArtworkModelLoaderFactory : ModelLoaderFactory<LocalArtworkProvider, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<LocalArtworkProvider, InputStream> {
        return LocalArtworkModelLoader()
    }

    override fun teardown() {
    }

}