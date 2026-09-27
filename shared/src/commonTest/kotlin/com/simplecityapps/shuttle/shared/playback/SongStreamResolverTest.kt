package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.shuttle.model.Song
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class SongStreamResolverTest {
    private class FakeStreamUrls(
        private val scheme: String
    ) : StreamUrlProvider {
        override fun handles(scheme: String?): Boolean = scheme == this.scheme

        override fun streamUrl(
            song: Song,
            startPositionMs: Long
        ): String = if (song.name == "signed out") {
            throw IllegalStateException("Failed to authenticate")
        } else {
            "https://$scheme.example/Audio/${song.path.substringAfterLast('/')}/universal" +
                if (startPositionMs > 0) "?StartTimeTicks=${startPositionMs * 10_000}" else ""
        }
    }

    private val resolver = SongStreamResolver(listOf(FakeStreamUrls("jellyfin"), FakeStreamUrls("emby")))

    @Test
    fun serverSongsStreamFromTheProviderThatHandlesTheirPath() = runTest {
        resolver.resolve(songAt(path = "jellyfin://item/abc"), 0) shouldBe
            IosStream(url = "https://jellyfin.example/Audio/abc/universal", opensAtPosition = true)
        resolver.resolve(songAt(path = "emby://item/def"), 0) shouldBe
            IosStream(url = "https://emby.example/Audio/def/universal", opensAtPosition = true)
    }

    @Test
    fun aServerStreamOpensAtThePositionAskedFor() = runTest {
        resolver.resolve(songAt(path = "jellyfin://item/abc"), 30_000).url shouldBe
            "https://jellyfin.example/Audio/abc/universal?StartTimeTicks=300000000"
    }

    @Test
    fun aFilePathBecomesAnEscapedFileUrl() = runTest {
        resolver.resolve(songAt(path = "/Music/Björk/Post/03 Hyperballad.flac"), 0) shouldBe
            IosStream(url = "file:///Music/Bj%C3%B6rk/Post/03%20Hyperballad.flac", opensAtPosition = false)
    }

    @Test
    fun anyOtherPathPassesThrough() = runTest {
        resolver.resolve(songAt(path = "https://example.com/song.mp3"), 0) shouldBe IosStream(url = "https://example.com/song.mp3")
        resolver.resolve(songAt(path = "demo://1"), 0).url shouldBe "demo://1"
    }

    @Test
    fun aProviderThatCantBuildTheUrlFailsTheSong() = runTest {
        shouldThrow<IllegalStateException> { resolver.resolve(songAt(path = "jellyfin://item/abc", name = "signed out"), 0) }
    }

    private fun songAt(
        path: String,
        name: String = "Song"
    ) = song(id = 1, path = path).copy(name = name)
}
