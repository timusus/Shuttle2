package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObservePlayablePlaylistsTest {

    private val playlistRepository = FakePlaylistRepository()
    private val observePlayablePlaylists = ObservePlayablePlaylists(playlistRepository)

    private fun remote(id: Long, path: String) = createSong(id = id, mediaProvider = MediaProviderType.Jellyfin, path = path)

    @Test
    fun `no playlists emits an empty set`() = runTest {
        observePlayablePlaylists(emptyList(), emptySet()).first() shouldBe emptySet()
    }

    @Test
    fun `a playlist with a local song is playable`() = runTest {
        val playlist = createPlaylist(id = 1)
        playlistRepository.setSongsForPlaylist(playlist, listOf(remote(1, "jellyfin://item/1"), createSong(id = 2)))

        observePlayablePlaylists(listOf(playlist), emptySet()).first() shouldBe setOf(1L)
    }

    @Test
    fun `a playlist of server songs is playable only with a completed download`() = runTest {
        val playlist = createPlaylist(id = 1)
        playlistRepository.setSongsForPlaylist(playlist, listOf(remote(1, "jellyfin://item/1"), remote(2, "jellyfin://item/2")))

        observePlayablePlaylists(listOf(playlist), emptySet()).first() shouldBe emptySet()
        observePlayablePlaylists(listOf(playlist), setOf("jellyfin://item/2")).first() shouldBe setOf(1L)
    }

    @Test
    fun `a downloaded song beyond the cover songs makes the playlist playable`() = runTest {
        val playlist = createPlaylist(id = 1)
        val songs = (1L..6L).map { remote(it, "jellyfin://item/$it") }
        playlistRepository.setSongsForPlaylist(playlist, songs)

        observePlayablePlaylists(listOf(playlist), setOf("jellyfin://item/6")).first() shouldBe setOf(1L)
    }

    @Test
    fun `an empty playlist is not playable`() = runTest {
        val playlist = createPlaylist(id = 1)
        playlistRepository.setSongsForPlaylist(playlist, emptyList())

        observePlayablePlaylists(listOf(playlist), emptySet()).first() shouldBe emptySet()
    }

    @Test
    fun `reports each playlist on its own`() = runTest {
        val local = createPlaylist(id = 1)
        val server = createPlaylist(id = 2)
        playlistRepository.setSongsForPlaylist(local, listOf(createSong(id = 1)))
        playlistRepository.setSongsForPlaylist(server, listOf(remote(2, "jellyfin://item/2")))

        observePlayablePlaylists(listOf(local, server), emptySet()).first() shouldBe setOf(1L)
    }
}
