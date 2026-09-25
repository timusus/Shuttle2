package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.Playlist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObservePlaylistsTest {

    private val playlistRepository = FakePlaylistRepository()
    private val observePlaylists = TestMediaActions(playlistRepository = playlistRepository).observePlaylists

    @Test
    fun `emits the playlists and each change`() = runTest {
        val emissions = mutableListOf<List<Playlist>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observePlaylists().toList(emissions) }

        val playlists = listOf(createPlaylist(id = 1, name = "Road trip"))
        playlistRepository.setPlaylists(playlists)

        emissions shouldBe listOf(emptyList(), playlists)
    }
}
