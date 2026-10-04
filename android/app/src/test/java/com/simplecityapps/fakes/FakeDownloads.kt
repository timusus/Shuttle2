package com.simplecityapps.fakes

import android.net.Uri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.downloads.SongDownload
import com.simplecityapps.shuttle.downloads.SongDownloadManager
import com.simplecityapps.shuttle.downloads.SongDownloadRepository
import com.simplecityapps.shuttle.model.Song
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A [Uri] standing in for [value], off `Uri.parse` so this fake runs on plain JVM as well as Robolectric (#552). */
private fun fakeUri(value: String): Uri {
    val uri = mockk<Uri>(relaxed = true)
    every { uri.toString() } returns value
    return uri
}

/** Records the downloads started and removed. */
class FakeSongDownloadManager : SongDownloadManager {
    val downloaded = mutableListOf<Pair<Song, Uri>>()
    val downloadedMimeTypes = mutableListOf<String>()
    val removed = mutableListOf<Song>()

    override fun download(song: Song, uri: Uri, mimeType: String) {
        downloaded += song to uri
        downloadedMimeTypes += mimeType
    }

    override fun restart(path: String, mimeType: String, uri: Uri) {}

    override fun remove(song: Song) {
        removed += song
    }

    override fun removeAll() {}

    override fun setRequirements(wifiOnly: Boolean) {}
}

class FakeSongDownloadRepository : SongDownloadRepository {
    val downloads = MutableStateFlow<List<SongDownload>>(emptyList())

    override fun observeDownloads(): Flow<List<SongDownload>> = downloads

    override fun observeDownload(path: String): Flow<SongDownload?> = downloads.map { list -> list.firstOrNull { it.path == path } }

    override fun observeDownloadedPaths(): Flow<Set<String>> = downloads.map { list ->
        list.filter { it.state == SongDownload.State.Completed }.mapTo(mutableSetOf()) { it.path }
    }

    override suspend fun getDownload(path: String): SongDownload? = downloads.value.firstOrNull { it.path == path }
}

/**
 * Hands out download info for every song except those whose path is in [unavailable], with a MIME type distinct
 * from [Song.mimeType] so a test can tell whether the caller forwarded it rather than falling back to the song's own.
 */
class FakeMediaInfoProvider : MediaInfoProvider {
    val unavailable = mutableSetOf<String>()

    override fun handles(scheme: String?): Boolean = true

    override suspend fun getMediaInfo(song: Song, castCompatibilityMode: Boolean): MediaInfo = MediaInfo(fakeUri(song.path), song.mimeType, isRemote = true)

    override suspend fun downloadInfo(song: Song): DownloadInfo? = if (song.path in unavailable) null else DownloadInfo(fakeUri("https://example.com/download/${song.id}"), "audio/download-transcode")

    override suspend fun downloadFallbackUri(path: String, responseCode: Int): Uri? = null
}
