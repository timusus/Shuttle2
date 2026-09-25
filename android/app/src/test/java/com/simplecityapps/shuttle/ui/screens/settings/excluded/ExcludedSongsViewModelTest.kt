package com.simplecityapps.shuttle.ui.screens.settings.excluded

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ExcludedSongsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songRepository = FakeSongRepository()

    @Test
    fun `loads until the library arrives`() {
        ExcludedSongsViewModel(songRepository).uiState.value.loading shouldBe true
    }

    @Test
    fun `lists only excluded songs, by name`() = runTest(mainDispatcherRule.testDispatcher) {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "beta").copy(blacklisted = true),
                createSong(id = 2, name = "Kept"),
                createSong(id = 3, name = "Alpha").copy(blacklisted = true)
            )
        )
        val viewModel = ExcludedSongsViewModel(songRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        state.loading shouldBe false
        state.songs.map { it.id } shouldBe listOf(3L, 1L)
    }

    @Test
    fun `including a song clears its exclusion`() {
        val song = createSong(id = 7).copy(blacklisted = true)

        ExcludedSongsViewModel(songRepository).onInclude(song)

        songRepository.excludedChanges shouldBe listOf(listOf(7L) to false)
    }

    @Test
    fun `including all clears the list`() {
        ExcludedSongsViewModel(songRepository).onIncludeAll()

        songRepository.clearExcludeListCount shouldBe 1
    }
}
