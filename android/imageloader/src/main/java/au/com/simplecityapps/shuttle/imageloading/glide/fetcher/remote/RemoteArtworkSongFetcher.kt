package au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.HttpUrlFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.shuttle.model.Song
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteArtworkSongFetcher(
    private val song: Song,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val scope: CoroutineScope
) : DataFetcher<InputStream> {
    private var httpUrlFetcher: HttpUrlFetcher? = null

    private var job: Job? = null

    override fun loadData(
        priority: Priority,
        callback: DataFetcher.DataCallback<in InputStream>
    ) {
        job =
            scope.launch {
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

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.REMOTE
}
