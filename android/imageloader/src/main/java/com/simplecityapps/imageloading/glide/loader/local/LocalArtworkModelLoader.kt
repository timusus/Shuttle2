package com.simplecityapps.imageloading.glide.loader.local

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.simplecityapps.imageloading.glide.loader.common.ArtworkProvider
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface LocalArtworkProvider : ArtworkProvider {
    suspend fun getInputStream(): InputStream?
}

class LocalArtworkModelLoader(
    private val coroutineScope: CoroutineScope
) : ModelLoader<LocalArtworkProvider, InputStream> {
    override fun buildLoadData(
        model: LocalArtworkProvider,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> = ModelLoader.LoadData(ObjectKey(model.getCacheKey()), LocalArtworkDataFetcher(coroutineScope, model))

    override fun handles(model: LocalArtworkProvider): Boolean = true

    class Factory(
        private val coroutineScope: CoroutineScope
    ) : ModelLoaderFactory<LocalArtworkProvider, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<LocalArtworkProvider, InputStream> = LocalArtworkModelLoader(coroutineScope)

        override fun teardown() {
        }
    }

    /**
     * Glide's [DataFetcher] contract allows [loadData] to answer [callback] asynchronously from any thread, so this
     * fetches on [coroutineScope] instead of blocking one of Glide's own source-executor threads for the DB lookup
     * some [LocalArtworkProvider]s need; [cancel] then cancels the in-flight [job] if Glide loses interest first.
     */
    class LocalArtworkDataFetcher(
        private val coroutineScope: CoroutineScope,
        private val localArtworkProvider: LocalArtworkProvider
    ) : DataFetcher<InputStream> {
        private var job: Job? = null

        override fun getDataClass(): Class<InputStream> = InputStream::class.java

        override fun cleanup() {
        }

        override fun getDataSource(): DataSource = DataSource.REMOTE

        override fun cancel() {
            job?.cancel()
        }

        override fun loadData(
            priority: Priority,
            callback: DataFetcher.DataCallback<in InputStream>
        ) {
            job = coroutineScope.launch(Dispatchers.IO) {
                localArtworkProvider.getInputStream()?.let { inputStream ->
                    callback.onDataReady(inputStream)
                } ?: run {
                    callback.onLoadFailed(GlideException("Local artwork not found (${localArtworkProvider.javaClass.simpleName})"))
                }
            }
        }
    }
}
