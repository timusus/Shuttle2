package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartPlaylistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }

    private fun TestScope.viewModel(id: String): SmartPlaylistDetailViewModel {
        val viewModel = SmartPlaylistDetailViewModel(id, ObserveSongs(songRepository), FakeQueueManager())
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
    fun `history lists the songs played to the end, most recent first`() = runTest {
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
        state.smartPlaylist?.nameResId shouldBe MediaProviderR.string.playlist_title_history
        state.songs.map { it.name } shouldBe listOf("Just now", "Yesterday", "Last week")
    }

    @Test
    fun `recently added lists the songs from the last two weeks, newest first`() = runTest {
        val now = Clock.System.now()
        songRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Last week").copy(lastModified = now - 7.days),
                createSong(id = 2, name = "Last month").copy(lastModified = now - 30.days),
                createSong(id = 3, name = "Today").copy(lastModified = now - 1.hours),
            )
        )

        val viewModel = viewModel(SmartPlaylistId.RecentlyAdded.id)

        val state = viewModel.uiState.value
        state.smartPlaylist?.nameResId shouldBe MediaProviderR.string.playlist_title_recently_added
        state.songs.map { it.name } shouldBe listOf("Today", "Last week")
    }

    @Test
    fun `an unknown slug is not found`() = runTest {
        val viewModel = viewModel("no-such-playlist")

        viewModel.uiState.value.loading shouldBe false
        viewModel.uiState.value.smartPlaylist shouldBe null
        viewModel.uiState.value.songs shouldBe emptyList()
    }

    @Test
    fun `every smart playlist has a route that resolves back to it`() {
        SmartPlaylistId.entries.map { it.smartPlaylist.route()?.smartPlaylistId?.let(SmartPlaylistId::fromId) } shouldBe SmartPlaylistId.entries
    }
}
