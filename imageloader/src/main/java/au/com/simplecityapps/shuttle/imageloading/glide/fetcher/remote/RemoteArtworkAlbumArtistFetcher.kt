package au.com.simplecityapps.shuttle.imageloading.glide.fetcher.remote

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
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

    private var remoteArtworkSongFetcher: RemoteArtworkSongFetcher? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        job = scope.launch {
            withContext(Dispatchers.IO) {
                songRepository.getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(albumArtist.groupKey))))
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