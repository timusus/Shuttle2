package com.simplecityapps.shuttle.ui.screens.settings.excluded

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
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
    private val mediaActionHandler = TestMediaActions(songRepository = songRepository).handler

    private fun viewModel() = ExcludedSongsViewModel(ObserveSongs(songRepository), mediaActionHandler)

    @Test
    fun `loads until the library arrives`() {
        viewModel().uiState.value.loading shouldBe true
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
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        state.loading shouldBe false
        state.songs.map { it.id } shouldBe listOf(3L, 1L)
    }

    @Test
    fun `including a song clears its exclusion through the Include action`() = runTest(mainDispatcherRule.testDispatcher) {
        val song = createSong(id = 7).copy(blacklisted = true)

        viewModel().onInclude(song)

        songRepository.excludedChanges shouldBe listOf(listOf(7L) to false)
    }

    @Test
    fun `including all clears every excluded song through the Include action`() = runTest(mainDispatcherRule.testDispatcher) {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "beta").copy(blacklisted = true),
                createSong(id = 2, name = "Kept"),
                createSong(id = 3, name = "Alpha").copy(blacklisted = true)
            )
        )
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onIncludeAll()

        songRepository.excludedChanges shouldBe listOf(listOf(3L, 1L) to false)
    }
}
