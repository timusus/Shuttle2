package com.simplecityapps.shuttle.downloads

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import com.simplecityapps.shuttle.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/** Song downloads as recorded in Media3's download index, keyed by `Song.path`. */
interface SongDownloadRepository {
    fun observeDownloads(): Flow<List<SongDownload>>

    /** Emits null while [path] has no download. */
    fun observeDownload(path: String): Flow<SongDownload?>

    /** The paths whose download has completed, i.e. that play offline. */
    fun observeDownloadedPaths(): Flow<Set<String>>

    suspend fun getDownload(path: String): SongDownload?
}

/**
 * Reads the [DownloadIndex] again whenever the [DownloadManager] reports a change. The index only
 * records progress on a state change, so running downloads are overlaid from
 * [DownloadManager.getCurrentDownloads], which is polled while anything is downloading: Media3
 * reports progress through neither the index nor its listener.
 */
@UnstableApi
class DefaultSongDownloadRepository @Inject constructor(
    private val downloadManager: DownloadManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SongDownloadRepository {
    override fun observeDownloads(): Flow<List<SongDownload>> = observe { current ->
        val active = current.associateBy { it.request.id }
        downloadIndex.readAll().map { (active[it.request.id] ?: it).toSongDownload() }
    }

    override fun observeDownload(path: String): Flow<SongDownload?> = observe { current ->
        (current.firstOrNull { it.request.id == path } ?: downloadIndex.getDownload(path))?.toSongDownload()
    }

    override fun observeDownloadedPaths(): Flow<Set<String>> = observe {
        downloadIndex.readAll(Download.STATE_COMPLETED).mapTo(mutableSetOf()) { it.request.id }
    }

    override suspend fun getDownload(path: String): SongDownload? = withContext(ioDispatcher) {
        downloadManager.downloadIndex.getDownload(path)?.toSongDownload()
    }

    private val downloadIndex: DownloadIndex
        get() = downloadManager.downloadIndex

    /** Re-reads [read] on every change, passing the downloads the manager is currently running. */
    private fun <T> observe(read: DownloadIndex.(current: List<Download>) -> T): Flow<T> = currentDownloads()
        .map { current -> withContext(ioDispatcher) { downloadIndex.read(current) } }
        .distinctUntilChanged()

    /**
     * The manager's current downloads, on every change it reports and every [PROGRESS_INTERVAL_MS]
     * while one is downloading. Collected on the main thread, where the manager delivers its
     * callbacks and updates its download list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun currentDownloads(): Flow<List<Download>> = callbackFlow {
        val listener =
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    trySend(Unit)
                }

                override fun onDownloadRemoved(
                    downloadManager: DownloadManager,
                    download: Download
                ) {
                    trySend(Unit)
                }

                override fun onInitialized(downloadManager: DownloadManager) {
                    trySend(Unit)
                }
            }
        downloadManager.addListener(listener)
        send(Unit)
        awaitClose { downloadManager.removeListener(listener) }
    }
        .conflate()
        .transformLatest {
            emit(downloadManager.currentDownloads)
            while (downloadManager.currentDownloads.any { it.state == Download.STATE_DOWNLOADING }) {
                delay(PROGRESS_INTERVAL_MS)
                emit(downloadManager.currentDownloads)
            }
        }
        .flowOn(Dispatchers.Main)

    companion object {
        private const val PROGRESS_INTERVAL_MS = 1000L
    }
}

@UnstableApi
private fun DownloadIndex.readAll(vararg states: Int): List<Download> = getDownloads(*states).use { cursor ->
    buildList {
        while (cursor.moveToNext()) add(cursor.download)
    }
}
