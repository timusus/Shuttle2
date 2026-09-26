package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.PlaylistSong
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObservePlaylistSongsTest {

    private val playlistRepository = FakePlaylistRepository()
    private val observePlaylistSongs = TestMediaActions(playlistRepository = playlistRepository).observePlaylistSongs

    @Test
    fun `emits the playlist's songs and each change`() = runTest {
        val playlist = createPlaylist(id = 1)
        val emissions = mutableListOf<List<PlaylistSong>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observePlaylistSongs(playlist).toList(emissions) }

        val songs = listOf(createSong(id = 1, name = "One"))
        playlistRepository.setSongsForPlaylist(playlist, songs)

        emissions.last().map { it.song } shouldBe songs
    }
}
