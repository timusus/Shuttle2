package com.simplecityapps.shuttle.downloads

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeMediaInfoProvider
import com.simplecityapps.fakes.FakeSongDownloadManager
import com.simplecityapps.fakes.FakeSongDownloadRepository
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ServerSongDownloaderTest {

    private val songDownloadManager = FakeSongDownloadManager()
    private val mediaInfoProvider = FakeMediaInfoProvider()
    private val downloader = ServerSongDownloader(songDownloadManager, AggregateMediaInfoProvider(mutableSetOf(mediaInfoProvider)), FakeSongDownloadRepository())
    private val song = createSong(id = 1, mediaProvider = MediaProviderType.Jellyfin, path = "jellyfin://1")

    @Test
    fun `downloads from the provider's download URL`() = runTest {
        downloader.download(song) shouldBe true

        songDownloadManager.downloaded.map { it.first } shouldBe listOf(song)
        songDownloadManager.downloaded.single().second.toString() shouldBe "https://example.com/download/1"
    }

    @Test
    fun `downloads with the provider's mime type, not the song's own (#567)`() = runTest {
        downloader.download(song)

        songDownloadManager.downloadedMimeTypes shouldBe listOf("audio/download-transcode")
    }

    @Test
    fun `a song without a download URL is not downloaded`() = runTest {
        mediaInfoProvider.unavailable += song.path

        downloader.download(song) shouldBe false
        songDownloadManager.downloaded.shouldBeEmpty()
    }

    @Test
    fun `removing drops the song's download`() {
        downloader.remove(song)

        songDownloadManager.removed shouldBe listOf(song)
    }
}
