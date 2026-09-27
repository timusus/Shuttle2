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

        override fun streamUrl(song: Song): String = if (song.name == "signed out") {
            throw IllegalStateException("Failed to authenticate")
        } else {
            "https://$scheme.example/Audio/${song.path.substringAfterLast('/')}/universal"
        }
    }

    private val resolver = SongStreamResolver(listOf(FakeStreamUrls("jellyfin"), FakeStreamUrls("emby")))

    @Test
    fun serverSongsStreamFromTheProviderThatHandlesTheirPath() = runTest {
        resolver.resolve(songAt(path = "jellyfin://item/abc")) shouldBe IosStream(url = "https://jellyfin.example/Audio/abc/universal")
        resolver.resolve(songAt(path = "emby://item/def")) shouldBe IosStream(url = "https://emby.example/Audio/def/universal")
    }

    @Test
    fun aFilePathBecomesAnEscapedFileUrl() = runTest {
        resolver.resolve(songAt(path = "/Music/Björk/Post/03 Hyperballad.flac")).url shouldBe
            "file:///Music/Bj%C3%B6rk/Post/03%20Hyperballad.flac"
    }

    @Test
    fun anyOtherPathPassesThrough() = runTest {
        resolver.resolve(songAt(path = "https://example.com/song.mp3")).url shouldBe "https://example.com/song.mp3"
        resolver.resolve(songAt(path = "demo://1")).url shouldBe "demo://1"
    }

    @Test
    fun aProviderThatCantBuildTheUrlFailsTheSong() = runTest {
        shouldThrow<IllegalStateException> { resolver.resolve(songAt(path = "jellyfin://item/abc", name = "signed out")) }
    }

    private fun songAt(
        path: String,
        name: String = "Song"
    ) = song(id = 1, path = path).copy(name = name)
}
