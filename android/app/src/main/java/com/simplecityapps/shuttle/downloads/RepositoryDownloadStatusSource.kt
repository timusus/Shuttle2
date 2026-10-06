package com.simplecityapps.shuttle.downloads

import com.simplecityapps.shuttle.ui.actions.DownloadStatusSource
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class RepositoryDownloadStatusSource @Inject constructor(
    private val songDownloadRepository: SongDownloadRepository,
) : DownloadStatusSource {
    override fun observe(): Flow<DownloadStatuses> = songDownloadRepository.observeDownloads().map { downloads ->
        val states = downloads.mapNotNull { download -> download.status()?.let { download.path to it } }.toMap()
        val progress = downloads.filter { it.status() == SongDownloadStatus.Downloading }.associate { it.path to it.progress }
        val sizes = downloads.filter { it.status() != null }.associate { it.path to it.bytesDownloaded }
        DownloadStatuses(states, progress, sizes)
    }
}

/** Queued, running and stopped (waiting for Wi-Fi) downloads count as downloading; Failed and Removing show nothing. */
internal fun SongDownload.status(): SongDownloadStatus? = when (state) {
    SongDownload.State.Queued, SongDownload.State.Downloading, SongDownload.State.Stopped -> SongDownloadStatus.Downloading
    SongDownload.State.Completed -> SongDownloadStatus.Downloaded
    SongDownload.State.Failed, SongDownload.State.Removing -> null
}
