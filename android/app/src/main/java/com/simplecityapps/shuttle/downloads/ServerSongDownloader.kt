package com.simplecityapps.shuttle.downloads

import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.shuttle.downloads.SongDownloadManager
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Downloads a remote song from its server: the provider gives the URL and type, the downloads module fetches it. */
@ContributesBinding(AppScope::class)
class ServerSongDownloader @Inject constructor(
    private val songDownloadManager: SongDownloadManager,
    private val mediaInfoProvider: AggregateMediaInfoProvider,
    private val songDownloadRepository: SongDownloadRepository,
) : SongDownloader {
    override suspend fun download(song: Song): Boolean {
        val downloadInfo = mediaInfoProvider.downloadInfo(song) ?: return false
        songDownloadManager.download(song, downloadInfo.uri, downloadInfo.mimeType)
        return true
    }

    override fun remove(song: Song) = songDownloadManager.remove(song)

    override fun observeHeldPaths(): Flow<Set<String>> = songDownloadRepository.observeDownloads().map { downloads -> downloads.filter { it.state in HELD_STATES }.mapTo(mutableSetOf()) { it.path } }

    private companion object {
        /** A download in any of these states is on the device or on its way; Failed and Removing aren't. */
        val HELD_STATES = setOf(
            SongDownload.State.Queued,
            SongDownload.State.Downloading,
            SongDownload.State.Completed,
            SongDownload.State.Stopped,
        )
    }
}
