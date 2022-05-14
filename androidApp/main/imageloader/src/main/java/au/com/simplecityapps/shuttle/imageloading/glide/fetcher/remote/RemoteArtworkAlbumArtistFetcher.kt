package au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.HttpUrlFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.io.InputStream

class RemoteArtworkAlbumArtistFetcher(
    private val albumArtist: AlbumArtist,
    private val songRepository: SongRepository,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val scope: CoroutineScope,
) : DataFetcher<InputStream> {

    private var job: Job? = null

    private var httpUrlFetcher: HttpUrlFetcher? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        job = scope.launch {
            withContext(Dispatchers.IO) {
                songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(albumArtist.groupKey))))
                    .firstOrNull()
                    ?.firstOrNull()
                    ?.let { song ->
                        val url = remoteArtworkProvider.getArtistArtworkUrl(song)
                        if (url == null) {
                            callback.onLoadFailed(Exception("Url null"))
                            return@withContext
                        }
                        httpUrlFetcher = HttpUrlFetcher(GlideUrl(url), 10000)
                        httpUrlFetcher!!.loadData(priority, callback)
                    } ?: run {
                    callback.onLoadFailed(Exception("Failed to retrieve song"))
                }
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
