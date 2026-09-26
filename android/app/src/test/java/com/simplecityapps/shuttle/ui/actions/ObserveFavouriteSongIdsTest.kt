package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveFavouriteSongIdsTest {

    private val songRepository = FakeSongRepository()
    private val observeFavouriteSongIds = ObserveFavouriteSongIds(songRepository)

    @Test
    fun `emits the ids of the favourite songs`() = runTest {
        val now = Clock.System.now()
        songRepository.setSongs(listOf(createSong(id = 1).copy(favouritedAt = now), createSong(id = 2), createSong(id = 3).copy(favouritedAt = now)))

        val ids = observeFavouriteSongIds().take(2).toList()

        ids shouldBe listOf(emptySet(), setOf(1L, 3L))
    }

    @Test
    fun `is empty until the library has loaded`() = runTest {
        observeFavouriteSongIds().first() shouldBe emptySet()
    }
}
