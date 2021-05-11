package au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.HttpUrlFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.*
import java.io.InputStream

class RemoteArtworkSongFetcher(
    private val song: Song,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val scope: CoroutineScope,
) : DataFetcher<InputStream> {

    private var httpUrlFetcher: HttpUrlFetcher? = null

    private var job: Job? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        job = scope.launch {
            withContext(Dispatchers.IO) {
                val url = remoteArtworkProvider.getAlbumArtworkUrl(song)
                if (url == null) {
                    callback.onLoadFailed(Exception("Url null"))
                    return@withContext
                }
                httpUrlFetcher = HttpUrlFetcher(GlideUrl(url), 10000)
                httpUrlFetcher!!.loadData(priority, callback)
            }
        }
    }

    override fun cleanup() {
        httpUrlFetcher?.cleanup()
    }

    override fun cancel() {
        job?.cancel()
        httpUrlFetcher?.cancel()
    }

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }
}