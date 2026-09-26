package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UpdatePlaylistSortOrderTest {

    private val playlistRepository = FakePlaylistRepository()
    private val updatePlaylistSortOrder = TestMediaActions(playlistRepository = playlistRepository).updatePlaylistSortOrder

    @Test
    fun `persists the new sort order and direction`() = runTest {
        val playlist = createPlaylist(id = 1, sortOrder = PlaylistSongSortOrder.Position, sortDescending = false)
        playlistRepository.setPlaylists(listOf(playlist))

        updatePlaylistSortOrder(playlist, PlaylistSongSortOrder.SongName, true)

        val updated = playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(1)).first().first()
        updated.sortOrder shouldBe PlaylistSongSortOrder.SongName
        updated.sortDescending shouldBe true
    }
}
