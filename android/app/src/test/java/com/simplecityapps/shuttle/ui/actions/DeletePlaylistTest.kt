package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeletePlaylistTest {

    private val playlistRepository = FakePlaylistRepository()
    private val actions = TestMediaActions(playlistRepository = playlistRepository)

    @Test
    fun `deletes only the given playlist`() = runTest {
        val playlist = createPlaylist(id = 1, name = "Gone")
        val other = createPlaylist(id = 2, name = "Kept")
        playlistRepository.setPlaylists(listOf(playlist, other))

        actions.deletePlaylist(playlist)

        actions.observePlaylists().first() shouldBe listOf(other)
    }
}
