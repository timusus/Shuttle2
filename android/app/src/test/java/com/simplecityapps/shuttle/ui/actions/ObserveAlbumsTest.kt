package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.Album
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveAlbumsTest {

    private val albumRepository = FakeAlbumRepository()
    private val observeAlbums = TestMediaActions(albumRepository = albumRepository).observeAlbums

    @Test
    fun `emits the library's albums and each change`() = runTest {
        val emissions = mutableListOf<List<Album>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeAlbums().toList(emissions) }

        val albums = listOf(createAlbum(name = "Album A"))
        albumRepository.setAlbums(albums)

        emissions shouldBe listOf(emptyList(), albums)
    }
}
