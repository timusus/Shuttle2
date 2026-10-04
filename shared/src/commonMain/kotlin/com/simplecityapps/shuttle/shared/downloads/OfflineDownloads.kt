package com.simplecityapps.shuttle.shared.downloads

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.forPath
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import com.simplecityapps.shuttle.ui.actions.downloadActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Offline downloads of Jellyfin, Emby and Plex songs on iOS (docs/architecture/downloads.md), the counterpart of Android's
 * `ServerSongDownloader` over Media3's `DownloadManager`: the song's provider gives the URL ([StreamUrlProvider.downloadSource]),
 * the [transport] fetches and keeps the file, and this holds each song's [OfflineDownload] state, keyed by `Song.path`
 * as Android's is. The files are the record of what's downloaded: [downloads] starts from the ones [DownloadTransport.restore]
 * finds, so there's no table to drift from them. A failed download is remembered until the app quits or it's retried.
 *
 * A completion this hasn't heard of is kept: iOS finishes a background download while the app isn't running and reports
 * it at the next launch, before (or instead of) [DownloadTransport.Listener.onRunning]. Only a song the user removed
 * ([remove]) since it was last downloaded has its late file deleted again.
 *
 * The player plays a downloaded song from its file ([fileUrl]).
 */
class OfflineDownloads(
    private val streamUrls: Collection<StreamUrlProvider>,
    private val transport: DownloadTransport
) : SongDownloader {
    private val _downloads = MutableStateFlow<Map<String, OfflineDownload>>(emptyMap())

    /** The paths [remove]d since they were last downloaded, whose late reports change nothing and whose late files are deleted. */
    private val removedPaths = MutableStateFlow<Set<String>>(emptySet())

    /** Every song's download that's running, completed or failed, by `Song.path`. */
    val downloads: StateFlow<Map<String, OfflineDownload>> = _downloads.asStateFlow()

    init {
        transport.listener = object : DownloadTransport.Listener {
            override fun onRunning(path: String) {
                if (path in removedPaths.value) return
                update(path) { current -> current ?: OfflineDownload(OfflineDownload.State.Downloading, 0f) }
            }

            override fun onProgress(
                path: String,
                bytesWritten: Long,
                totalBytes: Long
            ) = update(path) { current ->
                // A late report from a download that's since been removed or finished changes nothing
                if (current?.state == OfflineDownload.State.Downloading) current.copy(progress = progress(bytesWritten, totalBytes)) else current
            }

            override fun onCompleted(path: String) {
                // Removed while it was finishing: the file arrived after the removal deleted it
                if (path in removedPaths.value) return transport.remove(path)
                // Anything else is kept, including one finished while the app wasn't running, which nothing had reported
                update(path) { OfflineDownload(OfflineDownload.State.Completed, 1f) }
            }

            override fun onFailed(path: String) = update(path) { current -> current?.copy(state = OfflineDownload.State.Failed) }
        }
        val restored = transport.restore().associateWith { OfflineDownload(OfflineDownload.State.Completed, 1f) }
        _downloads.update { restored + it }
    }

    override suspend fun download(song: Song): Boolean {
        if (_downloads.value[song.path]?.state.let { it == OfflineDownload.State.Downloading || it == OfflineDownload.State.Completed }) return true
        val source = streamUrls.forPath(song.path)?.downloadSource(song) ?: return false
        removedPaths.update { it - song.path }
        _downloads.update { it + (song.path to OfflineDownload(OfflineDownload.State.Downloading, 0f)) }
        transport.start(song.path, source)
        return true
    }

    override fun remove(song: Song) {
        removedPaths.update { it + song.path }
        _downloads.update { it - song.path }
        transport.remove(song.path)
    }

    /** Running and completed downloads: [SongDownloader]'s held paths. A failed one isn't held, so it offers Download again. */
    override fun observeHeldPaths(): Flow<Set<String>> = downloads
        .map { downloads -> downloads.filterValues { it.state != OfflineDownload.State.Failed }.keys }
        .distinctUntilChanged()

    /** The `file://` URL [path] (a `Song.path`) plays from when its download has completed, else null. */
    fun fileUrl(path: String): String? = if (_downloads.value[path]?.state == OfflineDownload.State.Completed) transport.fileUrl(path) else null

    /** How far [songs]' downloads have got, read from [downloads]' current value, for a detail header's indicator and menu. */
    fun summary(songs: List<Song>): DownloadSummary {
        val downloads = _downloads.value
        val remote = songs.filter { it.mediaProvider.remote }.map { downloads[it.path] }
        val held = songs.mapNotNullTo(mutableSetOf()) { song -> song.path.takeIf { downloads[it]?.state.let { state -> state != null && state != OfflineDownload.State.Failed } } }
        val actions = downloadActions(songs, held)
        return DownloadSummary(
            status = when {
                remote.isEmpty() -> DownloadSummary.Status.NotDownloaded
                remote.any { it?.state == OfflineDownload.State.Downloading } -> DownloadSummary.Status.Downloading
                remote.all { it?.state == OfflineDownload.State.Completed } -> DownloadSummary.Status.Downloaded
                else -> DownloadSummary.Status.NotDownloaded
            },
            canDownload = MediaActionType.Download in actions,
            canRemove = MediaActionType.RemoveDownload in actions
        )
    }

    private inline fun update(
        path: String,
        crossinline transform: (OfflineDownload?) -> OfflineDownload?
    ) = _downloads.update { downloads ->
        val current = downloads[path]
        when (val next = transform(current)) {
            current -> downloads
            null -> downloads - path
            else -> downloads + (path to next)
        }
    }

    private fun progress(
        bytesWritten: Long,
        totalBytes: Long
    ): Float = if (totalBytes > 0) (bytesWritten.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** A song's download. [progress] is 0..1; 0 while the size is unknown, 1 once completed. */
data class OfflineDownload(
    val state: State,
    val progress: Float
) {
    enum class State {
        Downloading,
        Completed,
        Failed
    }
}

/**
 * Some songs' downloads at a glance: [status] Downloaded once every remote song's has completed, Downloading while any is
 * running; [canDownload] and [canRemove] are the shared actions' rule ([downloadActions]).
 */
data class DownloadSummary(
    val status: Status,
    val canDownload: Boolean,
    val canRemove: Boolean
) {
    enum class Status {
        NotDownloaded,
        Downloading,
        Downloaded
    }
}
