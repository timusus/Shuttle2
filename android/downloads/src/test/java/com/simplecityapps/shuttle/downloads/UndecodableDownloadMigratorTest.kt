package com.simplecityapps.shuttle.downloads

import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UndecodableDownloadMigratorTest {
    private val transcode = DownloadInfo(Uri.parse("https://server/transcode/alac1"), "audio/aac")

    private val songDownloadManager = RecordingSongDownloadManager()

    @Test
    fun `a completed download of an undecodable song saved as the original is downloaded again as the transcode`() = runTest {
        val migrator = migrator(songs = listOf(alacSong), completed = listOf(request(ALAC_PATH, "audio/alac")))

        migrator.redownloadUndecodable()

        songDownloadManager.restarted shouldBe listOf(Triple(ALAC_PATH, "audio/aac", transcode.uri))
    }

    @Test
    fun `a download already made from the transcode is left alone, so the pass is idempotent`() = runTest {
        val migrator = migrator(songs = listOf(alacSong), completed = listOf(request(ALAC_PATH, "audio/aac")))

        migrator.redownloadUndecodable()
        migrator.redownloadUndecodable()

        songDownloadManager.restarted shouldBe emptyList()
    }

    @Test
    fun `a decodable download is left alone`() = runTest {
        val migrator = migrator(songs = listOf(flacSong), completed = listOf(request(FLAC_PATH, "audio/flac")))

        migrator.redownloadUndecodable()

        songDownloadManager.restarted shouldBe emptyList()
    }

    @Test
    fun `a download of a song that has left the library is left alone`() = runTest {
        val migrator = migrator(songs = emptyList(), completed = listOf(request(ALAC_PATH, "audio/alac")))

        migrator.redownloadUndecodable()

        songDownloadManager.restarted shouldBe emptyList()
    }

    @Test
    fun `a signed-out server leaves the download for the next start`() = runTest {
        val migrator = migrator(songs = listOf(alacSong), completed = listOf(request(ALAC_PATH, "audio/alac")), info = null)

        migrator.redownloadUndecodable()

        songDownloadManager.restarted shouldBe emptyList()
    }

    private fun migrator(
        songs: List<Song>,
        completed: List<DownloadRequest>,
        info: DownloadInfo? = transcode
    ): UndecodableDownloadMigrator {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseProvider = StandaloneDatabaseProvider(context)
        val index = DefaultDownloadIndex(databaseProvider)
        completed.forEach { index.putDownload(Download(it, Download.STATE_COMPLETED, 0, 0, 100, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)) }
        val cache = SimpleCache(File(context.cacheDir, "migrator-${System.nanoTime()}"), NoOpCacheEvictor(), databaseProvider)
        val downloadManager = runOnMainThreadBlocking {
            DownloadManager(context, databaseProvider, cache, DefaultHttpDataSource.Factory(), Runnable::run)
        }
        return UndecodableDownloadMigrator(
            downloadManager,
            songDownloadManager,
            AggregateMediaInfoProvider(mutableSetOf(AlacProvider(info))),
            FakeSongRepository(songs),
            Dispatchers.Unconfined
        )
    }

    private fun request(
        path: String,
        mimeType: String
    ) = DownloadRequest.Builder(path, Uri.parse("https://server/download/$path")).setMimeType(mimeType).build()

    private val alacSong = testSong(ALAC_PATH, mimeType = "audio/alac", audioCodec = "alac")
    private val flacSong = testSong(FLAC_PATH, mimeType = "audio/flac", audioCodec = "flac")

    private companion object {
        const val ALAC_PATH = "jellyfin://item/alac1"
        const val FLAC_PATH = "jellyfin://item/flac1"
    }
}

/** A Jellyfin stand-in whose ALAC songs can't be decoded and download as [info]. */
private class AlacProvider(private val info: DownloadInfo?) : MediaInfoProvider {
    override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean,
        playId: String?
    ): MediaInfo = error("not called")

    override suspend fun downloadInfo(song: Song): DownloadInfo? = info

    override suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo? = error("not called")

    override fun downloadsAsTranscode(song: Song): Boolean = song.audioCodec == "alac"
}
