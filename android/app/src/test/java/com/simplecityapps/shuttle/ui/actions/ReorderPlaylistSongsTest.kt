package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.PlaylistSong
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReorderPlaylistSongsTest {

    private val playlistRepository = FakePlaylistRepository()
    private val reorderPlaylistSongs = TestMediaActions(playlistRepository = playlistRepository).reorderPlaylistSongs

    @Test
    fun `persists the new order`() = runTest {
        val playlist = createPlaylist(id = 1)
        val reordered = listOf(PlaylistSong(id = 0, sortOrder = 0, song = createSong(id = 2)), PlaylistSong(id = 1, sortOrder = 1, song = createSong(id = 1)))

        reorderPlaylistSongs(playlist, reordered)

        playlistRepository.reorderedSongs shouldBe reordered
    }
}
