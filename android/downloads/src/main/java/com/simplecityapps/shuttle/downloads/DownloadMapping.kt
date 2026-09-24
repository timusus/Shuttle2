package com.simplecityapps.shuttle.downloads

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements

/**
 * A request to download [path] (`Song.path`) from [uri]. Both the request id and the cache key
 * are the song's path, never the URL: the URL changes on every call and carries a token, so
 * keying on it would orphan the download the next time the token rotates.
 */
@UnstableApi
internal fun downloadRequest(
    path: String,
    mimeType: String,
    uri: Uri
): DownloadRequest = DownloadRequest.Builder(path, uri)
    // An audio mime type keeps DownloadManager on its progressive downloader, whatever the URL looks like.
    .setMimeType(mimeType)
    .setCustomCacheKey(path)
    .build()

/** Wi-Fi only waits for an unmetered network; otherwise any network will do. */
@UnstableApi
internal fun downloadRequirements(wifiOnly: Boolean): Requirements = Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

@UnstableApi
internal fun Download.toSongDownload(): SongDownload = SongDownload(
    path = request.id,
    state = songDownloadState(),
    progress = when {
        state == Download.STATE_COMPLETED -> 1f
        contentLength > 0 -> (bytesDownloaded.toFloat() / contentLength).coerceIn(0f, 1f)
        else -> 0f
    },
    bytesDownloaded = bytesDownloaded,
    contentLength = contentLength
)

@UnstableApi
private fun Download.songDownloadState(): SongDownload.State = when (state) {
    Download.STATE_QUEUED, Download.STATE_RESTARTING -> SongDownload.State.Queued
    Download.STATE_DOWNLOADING -> SongDownload.State.Downloading
    Download.STATE_COMPLETED -> SongDownload.State.Completed
    Download.STATE_STOPPED -> SongDownload.State.Stopped
    Download.STATE_REMOVING -> SongDownload.State.Removing
    else -> SongDownload.State.Failed
}
