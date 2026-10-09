package com.simplecityapps.shuttle.downloads

import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DownloadFallbackObserverTest {
    private lateinit var downloadManager: DownloadManager
    private lateinit var songDownloadManager: RecordingSongDownloadManager
    private lateinit var mediaInfoProvider: AggregateMediaInfoProvider
    private lateinit var fallbackProvider: FakeMediaInfoProvider
    private lateinit var observer: DownloadFallbackObserver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(context.cacheDir, NoOpCacheEvictor(), databaseProvider)
        downloadManager = runOnMainThreadBlocking {
            DownloadManager(context, databaseProvider, cache, DefaultHttpDataSource.Factory(), Runnable::run)
        }
        songDownloadManager = RecordingSongDownloadManager()
        fallbackProvider = FakeMediaInfoProvider()
        mediaInfoProvider = AggregateMediaInfoProvider(mutableSetOf(fallbackProvider))
        observer = DownloadFallbackObserver(downloadManager, songDownloadManager, mediaInfoProvider, SongLookup(FakeSongRepository(listOf(testSong(PATH))), Dispatchers.Unconfined))
    }

    @Test
    fun `a 403 retries once with the fallback url and disables the stored permission`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")

        observer.onDownloadChanged(failedDownload(responseCode = 403), invalidResponseCode(403), backgroundScope)
        runCurrent()

        songDownloadManager.restarted shouldBe listOf(Triple(PATH, "audio/flac", Uri.parse("https://server/stream/abc123")))
        fallbackProvider.disableCalled shouldBe true
    }

    @Test
    fun `the retry restarts the download rather than resuming the failed attempt's bytes`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")

        observer.onDownloadChanged(failedDownload(responseCode = 403), invalidResponseCode(403), backgroundScope)
        runCurrent()

        songDownloadManager.restarted.map { it.first } shouldBe listOf(PATH)
        songDownloadManager.downloaded shouldBe emptyList()
    }

    @Test
    fun `a 401 also retries with the fallback url`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")

        observer.onDownloadChanged(failedDownload(responseCode = 401), invalidResponseCode(401), backgroundScope)
        runCurrent()

        songDownloadManager.restarted shouldBe listOf(Triple(PATH, "audio/flac", Uri.parse("https://server/stream/abc123")))
    }

    @Test
    fun `the retry records the fallback's own type, so an ALAC song's transcode isn't saved as ALAC`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/transcode/abc123"), "audio/aac")

        observer.onDownloadChanged(failedDownload(responseCode = 401), invalidResponseCode(401), backgroundScope)
        runCurrent()

        songDownloadManager.restarted shouldBe listOf(Triple(PATH, "audio/aac", Uri.parse("https://server/transcode/abc123")))
        fallbackProvider.fallbackSong?.path shouldBe PATH
    }

    @Test
    fun `a song that has left the library has no fallback`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")
        val observer = DownloadFallbackObserver(downloadManager, songDownloadManager, mediaInfoProvider, SongLookup(FakeSongRepository(emptyList()), Dispatchers.Unconfined))

        observer.onDownloadChanged(failedDownload(responseCode = 401), invalidResponseCode(401), backgroundScope)
        runCurrent()

        songDownloadManager.restarted shouldBe emptyList()
    }

    @Test
    fun `only retries once per path`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")

        observer.onDownloadChanged(failedDownload(responseCode = 403), invalidResponseCode(403), backgroundScope)
        runCurrent()
        observer.onDownloadChanged(failedDownload(responseCode = 403), invalidResponseCode(403), backgroundScope)
        runCurrent()

        songDownloadManager.restarted.size shouldBe 1
    }

    @Test
    fun `a 403 wrapped in another exception still retries`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")

        observer.onDownloadChanged(failedDownload(responseCode = 403), java.io.IOException("wrapped", invalidResponseCode(403)), backgroundScope)
        runCurrent()

        songDownloadManager.restarted shouldBe listOf(Triple(PATH, "audio/flac", Uri.parse("https://server/stream/abc123")))
    }

    @Test
    fun `a non-auth failure is not retried`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")

        observer.onDownloadChanged(failedDownload(responseCode = 500), invalidResponseCode(500), backgroundScope)
        runCurrent()

        songDownloadManager.restarted shouldBe emptyList()
    }

    @Test
    fun `a download that is not failed is ignored`() = runTest {
        fallbackProvider.fallback = DownloadInfo(Uri.parse("https://server/stream/abc123"), "audio/flac")

        observer.onDownloadChanged(queuedDownload(), null, backgroundScope)
        runCurrent()

        songDownloadManager.restarted shouldBe emptyList()
    }

    private fun failedDownload(responseCode: Int) = Download(
        DownloadRequest.Builder(PATH, Uri.parse("https://server/download/abc123?token=stale")).setMimeType("audio/flac").build(),
        Download.STATE_FAILED,
        0,
        0,
        0,
        Download.STOP_REASON_NONE,
        Download.FAILURE_REASON_UNKNOWN
    )

    private fun queuedDownload() = Download(
        DownloadRequest.Builder(PATH, Uri.parse("https://server/download/abc123?token=stale")).setMimeType("audio/flac").build(),
        Download.STATE_QUEUED,
        0,
        0,
        0,
        Download.STOP_REASON_NONE,
        Download.FAILURE_REASON_NONE
    )

    private fun invalidResponseCode(responseCode: Int) = HttpDataSource.InvalidResponseCodeException(
        responseCode,
        "message",
        null,
        emptyMap(),
        androidx.media3.datasource.DataSpec(Uri.parse("https://server/download/abc123?token=stale")),
        ByteArray(0)
    )

    private companion object {
        const val PATH = "jellyfin://item/abc123"
    }
}

internal class RecordingSongDownloadManager : SongDownloadManager {
    val downloaded = mutableListOf<Song>()
    val restarted = mutableListOf<Triple<String, String, Uri>>()

    override fun download(
        song: Song,
        uri: Uri,
        mimeType: String
    ) {
        downloaded += song
    }

    override fun restart(
        path: String,
        mimeType: String,
        uri: Uri
    ) {
        restarted += Triple(path, mimeType, uri)
    }

    override fun remove(song: Song) = Unit

    override fun remove(path: String) = Unit

    override fun setRequirements(wifiOnly: Boolean) = Unit
}

private class FakeMediaInfoProvider : MediaInfoProvider {
    var fallback: DownloadInfo? = null
    var fallbackSong: Song? = null
    var disableCalled = false

    override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean,
        playId: String?
    ): MediaInfo = error("not called")

    override suspend fun downloadInfo(song: Song): DownloadInfo? = error("not called")

    override suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo? {
        disableCalled = true
        fallbackSong = song
        return fallback
    }
}
