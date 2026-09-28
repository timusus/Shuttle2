package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SmartPlaylistDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }

    private fun TestScope.viewModel(id: String): SmartPlaylistDetailViewModel {
        val viewModel = SmartPlaylistDetailViewModel(id, ObserveSongs(songRepository), ObserveCurrentSong(FakeQueueOperations()))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `resolves the playlist by its slug and sorts its songs by the playlist's own order`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1, name = "Rare", playCount = 2), createSong(id = 2, name = "Loved", playCount = 9)))

        val viewModel = viewModel(SmartPlaylistId.MostPlayed.id)

        val state = viewModel.uiState.value
        state.loading shouldBe false
        state.smartPlaylist shouldBe SmartPlaylistId.MostPlayed.smartPlaylist
        state.songs.map { it.name } shouldBe listOf("Loved", "Rare")
    }

    @Test
    fun `history lists the songs played to the end - most recent first`() = runTest {
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Last week", lastCompleted = Instant.fromEpochSeconds(1_000)),
                createSong(id = 2, name = "Never finished", lastCompleted = null),
                createSong(id = 3, name = "Just now", lastCompleted = Instant.fromEpochSeconds(9_000)),
                createSong(id = 4, name = "Yesterday", lastCompleted = Instant.fromEpochSeconds(5_000)),
            )
        )

        val viewModel = viewModel(SmartPlaylistId.History.id)

        val state = viewModel.uiState.value
        state.smartPlaylist?.id shouldBe SmartPlaylistId.History
        state.songs.map { it.name } shouldBe listOf("Just now", "Yesterday", "Last week")
    }

    @Test
    fun `recently added lists the songs from the last two weeks - newest first`() = runTest {
        val now = Clock.System.now()
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Last week").copy(dateAdded = now - 7.days),
                createSong(id = 2, name = "Last month").copy(dateAdded = now - 30.days),
                createSong(id = 3, name = "Today").copy(dateAdded = now - 1.hours),
            )
        )

        val viewModel = viewModel(SmartPlaylistId.RecentlyAdded.id)

        val state = viewModel.uiState.value
        state.smartPlaylist?.id shouldBe SmartPlaylistId.RecentlyAdded
        state.songs.map { it.name } shouldBe listOf("Today", "Last week")
    }

    @Test
    fun `an unknown slug is not found`() = runTest {
        val viewModel = viewModel("no-such-playlist")

        viewModel.uiState.value.loading shouldBe false
        viewModel.uiState.value.smartPlaylist shouldBe null
        viewModel.uiState.value.songs shouldBe emptyList()
    }
}
