package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.TestMediaActions
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CreatePlaylistTest {

    private val playlistRepository = FakePlaylistRepository()
    private val createPlaylist = TestMediaActions(playlistRepository = playlistRepository).createPlaylist

    @Test
    fun `creates the playlist holding the selection's songs`() = runTest {
        val songs = listOf(createSong(id = 1), createSong(id = 2))

        val playlist = createPlaylist("Road trip", MediaSelection.Songs(songs))

        playlist.name shouldBe "Road trip"
        playlistRepository.created shouldBe listOf("Road trip" to songs)
    }

    @Test
    fun `creates an empty playlist without a selection`() = runTest {
        createPlaylist("Empty", null)

        playlistRepository.created shouldBe listOf("Empty" to null)
    }
}
