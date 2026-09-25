package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RenamePlaylistTest {

    private val playlistRepository = FakePlaylistRepository()
    private val actions = TestMediaActions(playlistRepository = playlistRepository)

    @Test
    fun `renames only the given playlist`() = runTest {
        val playlist = createPlaylist(id = 1, name = "Old")
        val other = createPlaylist(id = 2, name = "Other")
        playlistRepository.setPlaylists(listOf(playlist, other))

        actions.renamePlaylist(playlist, "New")

        actions.observePlaylists().first().map { it.name } shouldBe listOf("New", "Other")
    }
}
