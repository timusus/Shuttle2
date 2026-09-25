package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.createSmartPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
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

    private val playlistRepository = FakePlaylistRepository()
    private val songRepository = FakeSongRepository()

    private val recentlyAdded = createSmartPlaylist()
    private val mostPlayed = createSmartPlaylist(MediaProviderR.string.playlist_title_most_played, SongQuery.PlayCount(2, SongSortOrder.PlayCount))

    private fun TestScope.viewModel(id: String): SmartPlaylistDetailViewModel {
        playlistRepository.setSmartPlaylists(listOf(recentlyAdded, mostPlayed))
        val viewModel = SmartPlaylistDetailViewModel(id, playlistRepository, songRepository, FakeQueueManager())
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
        state.smartPlaylist shouldBe mostPlayed
        state.songs.map { it.name } shouldBe listOf("Loved", "Rare")
    }

    @Test
    fun `an unknown slug is not found`() = runTest {
        val viewModel = viewModel("no-such-playlist")

        viewModel.uiState.value.loading shouldBe false
        viewModel.uiState.value.smartPlaylist shouldBe null
        viewModel.uiState.value.songs shouldBe emptyList()
    }

    @Test
    fun `every smart playlist the repository offers has a route that resolves back to it`() {
        listOf(recentlyAdded, mostPlayed).map { it.route()?.smartPlaylistId?.let(SmartPlaylistId::fromId) } shouldBe
            listOf(SmartPlaylistId.RecentlyAdded, SmartPlaylistId.MostPlayed)
    }
}
