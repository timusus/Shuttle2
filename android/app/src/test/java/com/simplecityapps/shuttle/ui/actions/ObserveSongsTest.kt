package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveSongsTest {

    private val songRepository = FakeSongRepository()
    private val observeSongs = TestMediaActions(songRepository = songRepository).observeSongs

    @Test
    fun `emits nothing until the library has loaded, then each change`() = runTest {
        val emissions = mutableListOf<List<Song>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeSongs().toList(emissions) }

        emissions shouldBe emptyList()

        val first = listOf(createSong(id = 1))
        val second = first + createSong(id = 2)
        songRepository.setSongs(first)
        songRepository.setSongs(second)

        emissions shouldBe listOf(first, second)
    }

    @Test
    fun `passes the query to the repository`() = runTest {
        songRepository.applyQueryPredicates = true
        val match = createSong(id = 1, albumArtist = "Artist A")
        songRepository.setSongs(listOf(match, createSong(id = 2, albumArtist = "Artist B")))

        observeSongs(SongQuery.ArtistGroupKey(match.albumArtistGroupKey)).first() shouldBe listOf(match)
    }
}
