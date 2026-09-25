package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearPlaylistTest {

    private val playlistRepository = FakePlaylistRepository()
    private val clearPlaylist = TestMediaActions(playlistRepository = playlistRepository).clearPlaylist

    @Test
    fun `empties the playlist but keeps it`() = runTest {
        val playlist = createPlaylist(id = 1)
        playlistRepository.setPlaylists(listOf(playlist))
        playlistRepository.setSongsForPlaylist(playlist, listOf(createSong(id = 1), createSong(id = 2)))

        clearPlaylist(playlist)

        playlistRepository.getSongsForPlaylist(playlist).first() shouldBe emptyList()
        playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(1)).first() shouldBe listOf(playlist)
    }
}
