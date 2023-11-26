package au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.query.SongQuery
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteArtworkAlbumFetcher(
    private val album: Album,
    private val songRepository: SongRepository,
    private val remoteArtworkProvider: RemoteArtworkProvider,
    private val scope: CoroutineScope
) : DataFetcher<InputStream> {
    private var job: Job? = null

    private var remoteArtworkSongFetcher: RemoteArtworkSongFetcher? = null

    override fun loadData(
        priority: Priority,
        callback: DataFetcher.DataCallback<in InputStream>
    ) {
        job =
            scope.launch {
                withContext(Dispatchers.IO) {
                    songRepository.getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
                        .firstOrNull()
                        ?.firstOrNull()
                        ?.let { song ->
                            remoteArtworkSongFetcher = RemoteArtworkSongFetcher(song, remoteArtworkProvider, scope)
                            remoteArtworkSongFetcher!!.loadData(priority, callback)
                        } ?: run {
                        callback.onLoadFailed(Exception("Failed to retrieve song"))
                    }
                }
            }
    }

    override fun cleanup() {
        remoteArtworkSongFetcher?.cleanup()
    }

    override fun cancel() {
        remoteArtworkSongFetcher?.cancel()
        job?.cancel()
    }

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }
}
