package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote

import au.com.simplecityapps.shuttle.imageloading.glide.provider.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.okhttp3.OkHttpStreamFetcher
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import okhttp3.OkHttpClient
import retrofit2.Call
import java.io.IOException
import java.io.InputStream
import java.net.SocketException

class RemoteArtworkModelLoader(
    private val okHttpClient: OkHttpClient
) : ModelLoader<RemoteArtworkProvider, InputStream> {

    override fun buildLoadData(model: RemoteArtworkProvider, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData<InputStream>(ObjectKey(model.getCacheKey()), RemoteArtworkDataFetcher(okHttpClient, model))
    }

    override fun handles(model: RemoteArtworkProvider): Boolean {
        return true
    }
}

class RemoteArtworkDataFetcher(
    private val okHttpClient: OkHttpClient,
    private val remoteArtworkProvider: RemoteArtworkProvider
) : DataFetcher<InputStream> {

    private var okHttpStreamFetcher: OkHttpStreamFetcher? = null

    private var call: Call<out ArtworkUrlResult>? = null

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun cleanup() {
        okHttpStreamFetcher?.cleanup()
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }

    override fun cancel() {
        call?.cancel()
        okHttpStreamFetcher?.cancel()
    }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        call = remoteArtworkProvider.getArtworkUri()
            try {
                call!!.execute().body()?.artworkUrl
            } catch (e: SocketException) {
                null
            } catch (e: IOException) {
                null
            }
                ?.takeIf { url -> url.isNotBlank() }
                ?.let { url ->
                okHttpStreamFetcher = OkHttpStreamFetcher(okHttpClient, GlideUrl(url))
                okHttpStreamFetcher!!.loadData(priority, callback)
        } ?: callback.onLoadFailed(GlideException("Artwork url not found"))
    }
}