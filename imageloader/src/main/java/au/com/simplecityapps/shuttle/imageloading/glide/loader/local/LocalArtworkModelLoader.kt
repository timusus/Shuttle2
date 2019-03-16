package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import au.com.simplecityapps.shuttle.imageloading.glide.provider.local.LocalArtworkProvider
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern

class LocalArtworkModelLoader : ModelLoader<LocalArtworkProvider, InputStream> {

    override fun buildLoadData(model: LocalArtworkProvider, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData<InputStream>(ObjectKey(model.getCacheKey()), LocalArtworkDataFetcher(model))
    }

    override fun handles(model: LocalArtworkProvider): Boolean {
        return true
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
        return DataSource.LOCAL
    }

    override fun cancel() {

    }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {

        val pattern = Pattern.compile("(folder|cover|album).*\\.(jpg|jpeg|png)", Pattern.CASE_INSENSITIVE)

        val files = File(localArtworkProvider.getPath()).parentFile.listFiles { file -> pattern.matcher(file.name).matches() }

        files.firstOrNull { it.length() > 1024 }?.let { file ->
            return callback.onDataReady(file.inputStream())
        } ?: run {
            callback.onLoadFailed(GlideException("No suitable artwork found on disk"))
        }
    }
}