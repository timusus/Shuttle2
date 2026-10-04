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
    private val songDownloadRepository = FakeSongDownloadRepository()
    private val downloader = ServerSongDownloader(songDownloadManager, AggregateMediaInfoProvider(mutableSetOf(mediaInfoProvider)), songDownloadRepository)
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

    @Test
    fun `removing all of a type drops that server's downloads in any state - and leaves the others`() = runTest {
        songDownloadRepository.downloads.value = listOf(
            "jellyfin://item/1" to SongDownload.State.Completed,
            "jellyfin://item/2" to SongDownload.State.Failed,
            "emby://item/3" to SongDownload.State.Completed,
            "/music/4.flac" to SongDownload.State.Completed,
        ).map { (path, state) -> SongDownload(path, state, 1f, 0, 0) }

        downloader.removeAll(MediaProviderType.Jellyfin)

        songDownloadManager.removedPaths shouldBe listOf("jellyfin://item/1", "jellyfin://item/2")
    }

    @Test
    fun `removing all of a local type removes nothing`() = runTest {
        songDownloadRepository.downloads.value = listOf(SongDownload("/music/4.flac", SongDownload.State.Completed, 1f, 0, 0))

        downloader.removeAll(MediaProviderType.Shuttle)

        songDownloadManager.removedPaths.shouldBeEmpty()
    }
}
