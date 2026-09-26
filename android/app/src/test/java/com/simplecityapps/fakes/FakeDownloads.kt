package com.simplecityapps.fakes

import android.net.Uri
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
    val removed = mutableListOf<Song>()

    override fun download(song: Song, uri: Uri) {
        downloaded += song to uri
    }

    override fun download(path: String, mimeType: String, uri: Uri) {}

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

/** Hands out a download URL for every song except those whose path is in [unavailable]. */
class FakeMediaInfoProvider : MediaInfoProvider {
    val unavailable = mutableSetOf<String>()

    override fun handles(scheme: String?): Boolean = true

    override suspend fun getMediaInfo(song: Song, castCompatibilityMode: Boolean): MediaInfo = MediaInfo(fakeUri(song.path), song.mimeType, isRemote = true)

    override suspend fun downloadUri(song: Song): Uri? = if (song.path in unavailable) null else fakeUri("https://example.com/download/${song.id}")

    override suspend fun downloadFallbackUri(path: String, responseCode: Int): Uri? = null
}
