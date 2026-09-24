package com.simplecityapps.shuttle.downloads

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadProgress
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadMappingTest {
    @Test
    fun `a request is keyed by the song path, not the tokenised stream URL`() {
        val song = song(path = "jellyfin://item/abc123")
        val request = downloadRequest(song.path, song.mimeType, Uri.parse("https://server/Audio/abc123/universal?api_key=secret"))

        request.id shouldBe "jellyfin://item/abc123"
        request.customCacheKey shouldBe "jellyfin://item/abc123"
        request.uri.toString() shouldBe "https://server/Audio/abc123/universal?api_key=secret"
        request.mimeType shouldBe "audio/flac"
    }

    @Test
    fun `Media3 states map to song download states`() {
        mapOf(
            Download.STATE_QUEUED to SongDownload.State.Queued,
            Download.STATE_RESTARTING to SongDownload.State.Queued,
            Download.STATE_DOWNLOADING to SongDownload.State.Downloading,
            Download.STATE_COMPLETED to SongDownload.State.Completed,
            Download.STATE_FAILED to SongDownload.State.Failed,
            Download.STATE_STOPPED to SongDownload.State.Stopped,
            Download.STATE_REMOVING to SongDownload.State.Removing
        ).forEach { (state, expected) ->
            download(state = state).toSongDownload().state shouldBe expected
        }
    }

    @Test
    fun `progress is the share of the content length downloaded`() {
        val mapped = download(state = Download.STATE_DOWNLOADING, contentLength = 400, bytesDownloaded = 100).toSongDownload()

        mapped.path shouldBe PATH
        mapped.progress shouldBe 0.25f
        mapped.bytesDownloaded shouldBe 100
        mapped.contentLength shouldBe 400
    }

    @Test
    fun `progress is zero while the length is unknown, and one once completed`() {
        download(state = Download.STATE_DOWNLOADING, contentLength = C.LENGTH_UNSET.toLong(), bytesDownloaded = 100)
            .toSongDownload().progress shouldBe 0f
        download(state = Download.STATE_COMPLETED, contentLength = C.LENGTH_UNSET.toLong(), bytesDownloaded = 100)
            .toSongDownload().progress shouldBe 1f
    }

    @Test
    fun `wifi only needs an unmetered network, otherwise any network`() {
        downloadRequirements(wifiOnly = true).isUnmeteredNetworkRequired shouldBe true
        downloadRequirements(wifiOnly = false).isUnmeteredNetworkRequired shouldBe false
        downloadRequirements(wifiOnly = false).isNetworkRequired shouldBe true
    }

    private fun download(
        state: Int,
        contentLength: Long = 1000,
        bytesDownloaded: Long = 0
    ) = Download(
        DownloadRequest.Builder(PATH, Uri.parse("https://server/stream")).build(),
        state,
        0,
        0,
        contentLength,
        // Media3 insists a stopped download has a stop reason and only a failed one a failure reason.
        if (state == Download.STATE_STOPPED) 1 else Download.STOP_REASON_NONE,
        if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE,
        DownloadProgress().apply { this.bytesDownloaded = bytesDownloaded }
    )

    private companion object {
        const val PATH = "jellyfin://item/abc123"
    }
}

internal fun song(path: String) = Song(
    id = 1,
    name = "Song",
    albumArtist = null,
    artists = emptyList(),
    album = null,
    track = null,
    disc = null,
    duration = 0,
    date = null,
    genres = emptyList(),
    path = path,
    size = 0,
    mimeType = "audio/flac",
    lastModified = null,
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    mediaProvider = MediaProviderType.Jellyfin,
    lyrics = null,
    grouping = null,
    bitRate = null,
    bitDepth = null,
    sampleRate = null,
    channelCount = null
)
