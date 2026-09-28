package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbumArtist
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.AlbumArtist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveArtistsTest {

    private val albumArtistRepository = FakeAlbumArtistRepository()
    private val observeArtists = TestMediaActions(albumArtistRepository = albumArtistRepository).observeArtists

    @Test
    fun `emits the library's album artists and each change`() = runTest {
        val emissions = mutableListOf<List<AlbumArtist>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeArtists().toList(emissions) }

        val albumArtists = listOf(createAlbumArtist(name = "Artist A"))
        albumArtistRepository.setAlbumArtists(albumArtists)

        emissions shouldBe listOf(emptyList(), albumArtists)
    }
}
