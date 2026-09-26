package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveFavouriteSongIdsTest {

    private val playlistRepository = FakePlaylistRepository()
    private val observeFavouriteSongIds = ObserveFavouriteSongIds(playlistRepository)

    @Test
    fun `emits the ids of the songs in the Favorites playlist`() = runTest {
        val favorites = createPlaylist(id = 1L)
        playlistRepository.favorites = favorites
        playlistRepository.setSongsForPlaylist(favorites, listOf(createSong(id = 1), createSong(id = 2)))

        val ids = observeFavouriteSongIds().take(2).toList()

        ids shouldBe listOf(emptySet(), setOf(1L, 2L))
    }

    @Test
    fun `is empty rather than an error when there's no Favorites playlist`() = runTest {
        observeFavouriteSongIds().first() shouldBe emptySet()
    }
}
