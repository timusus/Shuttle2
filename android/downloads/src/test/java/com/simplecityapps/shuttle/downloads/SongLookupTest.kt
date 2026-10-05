package com.simplecityapps.shuttle.downloads

import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SongLookupTest {
    @Test
    fun `a burst of failures reuses one library load`() = runTest {
        val repository = FakeSongRepository(listOf(testSong(PATH)))
        val lookup = SongLookup(repository, Dispatchers.Unconfined)
        var time = 0L
        lookup.now = { time }

        lookup.songAt(PATH)
        time += 1_000
        lookup.songAt("jellyfin://item/other")

        repository.loadCount shouldBe 1
    }

    @Test
    fun `a quiet period reloads the library`() = runTest {
        val repository = FakeSongRepository(listOf(testSong(PATH)))
        val lookup = SongLookup(repository, Dispatchers.Unconfined)
        var time = 0L
        lookup.now = { time }

        lookup.songAt(PATH)
        time += 10_000
        lookup.songAt(PATH)

        repository.loadCount shouldBe 2
    }

    @Test
    fun `another provider's failures load their own library`() = runTest {
        val embySong = testSong(EMBY_PATH, mediaProvider = MediaProviderType.Emby)
        val repository = FakeSongRepository(listOf(embySong))
        val lookup = SongLookup(repository, Dispatchers.Unconfined)

        lookup.songAt(EMBY_PATH) shouldBe embySong
        lookup.songAt("plex://item/other")

        repository.loadCount shouldBe 2
    }

    @Test
    fun `a path with an unknown provider has no song`() = runTest {
        val lookup = SongLookup(FakeSongRepository(emptyList()), Dispatchers.Unconfined)

        lookup.songAt("file:///storage/emulated/0/Music/song.flac") shouldBe null
    }

    private companion object {
        const val PATH = "jellyfin://item/abc123"
        const val EMBY_PATH = "emby://item/abc123"
    }
}
