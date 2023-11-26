package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.ArtworkProvider
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream

interface LocalArtworkProvider : ArtworkProvider {
    fun getInputStream(): InputStream?
}

class LocalArtworkModelLoader : ModelLoader<LocalArtworkProvider, InputStream> {
    override fun buildLoadData(
        model: LocalArtworkProvider,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> {
        return ModelLoader.LoadData(ObjectKey(model.getCacheKey()), LocalArtworkDataFetcher(model))
    }

    override fun handles(model: LocalArtworkProvider): Boolean {
        return true
    }

    class Factory : ModelLoaderFactory<LocalArtworkProvider, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<LocalArtworkProvider, InputStream> {
            return LocalArtworkModelLoader()
        }

        override fun teardown() {
        }
    }

    class LocalArtworkDataFetcher(
        private val localArtworkProvider: LocalArtworkProvider
    ) : DataFetcher<InputStream> {
        override fun getDataClass(): Class<InputStream> {
            return InputStream::class.java
        }

        override fun cleanup() {
        }

        override fun getDataSource(): DataSource {
            return DataSource.REMOTE
        }

        override fun cancel() {
        }

        override fun loadData(
            priority: Priority,
            callback: DataFetcher.DataCallback<in InputStream>
        ) {
            localArtworkProvider.getInputStream()?.let { inputStream ->
                callback.onDataReady(inputStream)
            } ?: run {
                callback.onLoadFailed(GlideException("Local artwork not found (${localArtworkProvider.javaClass.simpleName})"))
            }
        }
    }
}
