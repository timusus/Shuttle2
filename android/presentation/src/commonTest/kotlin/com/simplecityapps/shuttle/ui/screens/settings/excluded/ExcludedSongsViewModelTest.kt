package com.simplecityapps.shuttle.ui.screens.settings.excluded

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class ExcludedSongsViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val songRepository = FakeSongRepository()
    private val mediaActionHandler = TestMediaActions(songRepository = songRepository).handler

    private fun viewModel() = ExcludedSongsViewModel(ObserveSongs(songRepository), mediaActionHandler)

    @Test
    fun `loads until the library arrives`() {
        viewModel().uiState.value.loading shouldBe true
    }

    @Test
    fun `lists only excluded songs by name`() = runTest(mainDispatcher) {
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
    fun `including a song clears its exclusion through the Include action`() = runTest(mainDispatcher) {
        val song = createSong(id = 7).copy(blacklisted = true)

        viewModel().onInclude(song)

        songRepository.excludedChanges shouldBe listOf(listOf(7L) to false)
    }

    @Test
    fun `including all clears every excluded song through the Include action`() = runTest(mainDispatcher) {
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
