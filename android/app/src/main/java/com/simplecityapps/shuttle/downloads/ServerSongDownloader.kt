package com.simplecityapps.shuttle.downloads

import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.shuttle.downloads.SongDownloadManager
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Downloads a remote song from its server: the provider gives the URL and type, the downloads module fetches it. */
@ContributesBinding(AppScope::class)
class ServerSongDownloader @Inject constructor(
    private val songDownloadManager: SongDownloadManager,
    private val mediaInfoProvider: AggregateMediaInfoProvider,
) : SongDownloader {
    override suspend fun download(song: Song): Boolean {
        val downloadInfo = mediaInfoProvider.downloadInfo(song) ?: return false
        songDownloadManager.download(song, downloadInfo.uri, downloadInfo.mimeType)
        return true
    }

    override fun remove(song: Song) = songDownloadManager.remove(song)
}
