package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.TestMediaActions
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveSongsForGenreTest {

    private val genreRepository = FakeGenreRepository()
    private val observeSongsForGenre = TestMediaActions(genreRepository = genreRepository).observeSongsForGenre

    @Test
    fun `emits the genre's songs`() = runTest {
        val songs = listOf(createSong(id = 1, name = "Take Five"))
        genreRepository.setSongsForGenre("Jazz", songs)

        observeSongsForGenre("Jazz").first() shouldBe songs
    }
}
