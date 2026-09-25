package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AggregateMediaInfoProviderTest {
    /** Stands in for a remote provider: claims its scheme and returns a stream URL. */
    private class SchemeProvider(private val scheme: String) : MediaInfoProvider {
        override fun handles(uri: Uri): Boolean = uri.scheme == scheme

        override suspend fun getMediaInfo(
            song: Song,
            castCompatibilityMode: Boolean
        ): MediaInfo = MediaInfo(Uri.parse("https://$scheme.example/stream"), song.mimeType, isRemote = true)

        override suspend fun downloadUri(song: Song): Uri = Uri.parse("https://$scheme.example/download")

        override suspend fun downloadFallbackUri(
            path: String,
            responseCode: Int
        ): Uri = Uri.parse("https://$scheme.example/fallback")
    }

    private val provider = AggregateMediaInfoProvider(
        mutableSetOf(SchemeProvider("emby"), SchemeProvider("jellyfin"), SchemeProvider("plex"))
    )

    @Test
    fun `remote song paths reach the provider for their scheme`() = runTest {
        for (scheme in listOf("emby", "jellyfin", "plex")) {
            val info = provider.getMediaInfo(createSong("$scheme://item/107898"))

            info.path.toString() shouldBe "https://$scheme.example/stream"
            info.isRemote shouldBe true
        }
    }

    @Test
    fun `media store file paths with a hash stay file uris`() = runTest {
        val info = provider.getMediaInfo(createSong("/storage/emulated/0/Music/Track #1.mp3"))

        info.path.scheme shouldBe "file"
        info.path.path shouldBe "/storage/emulated/0/Music/Track #1.mp3"
        info.isRemote shouldBe false
    }

    @Test
    fun `content uris are parsed as they are`() = runTest {
        val path = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fa.flac"

        provider.getMediaInfo(createSong(path)).path.toString() shouldBe path
    }

    @Test
    fun `download uri reaches the provider for its scheme`() = runTest {
        provider.downloadUri(createSong("jellyfin://item/107898")).toString() shouldBe "https://jellyfin.example/download"
    }

    @Test
    fun `local songs have no download uri`() = runTest {
        provider.downloadUri(createSong("/storage/emulated/0/Music/Track #1.mp3")) shouldBe null
    }

    @Test
    fun `fallback uri reaches the provider for its scheme`() = runTest {
        provider.downloadFallbackUri("jellyfin://item/107898", 403).toString() shouldBe "https://jellyfin.example/fallback"
    }

    @Test
    fun `a remote song the stream policy refuses fails to resolve`() = runTest {
        val asked = mutableListOf<String>()
        val refusing = AggregateMediaInfoProvider(
            mutableSetOf(SchemeProvider("jellyfin")),
            ServerStreamPolicy { song ->
                asked += song.path
                false
            }
        )

        shouldThrow<ServerStreamDeniedException> { refusing.getMediaInfo(createSong("jellyfin://item/107898")) }
        asked shouldBe listOf("jellyfin://item/107898")
    }

    @Test
    fun `local songs never ask the stream policy`() = runTest {
        val refusing = AggregateMediaInfoProvider(mutableSetOf(SchemeProvider("jellyfin")), ServerStreamPolicy { error("asked") })

        refusing.getMediaInfo(createSong("/storage/emulated/0/Music/a.mp3")).isRemote shouldBe false
    }

    @Test
    fun `downloads don't ask the stream policy`() = runTest {
        val refusing = AggregateMediaInfoProvider(mutableSetOf(SchemeProvider("jellyfin")), ServerStreamPolicy { error("asked") })

        refusing.downloadUri(createSong("jellyfin://item/107898")).toString() shouldBe "https://jellyfin.example/download"
    }

    private fun createSong(path: String) = Song(
        id = 0,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
