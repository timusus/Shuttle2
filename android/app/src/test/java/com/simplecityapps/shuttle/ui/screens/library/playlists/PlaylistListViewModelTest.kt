package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.createPlaylist
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlistRepository = FakePlaylistRepository()
    private val sortPreferences = FakeSortPreferences()
    private val actions = TestMediaActions(playlistRepository = playlistRepository)

    private fun viewModel(): PlaylistListViewModel = PlaylistListViewModel(
        observePlaylists = actions.observePlaylists,
        createPlaylist = actions.createPlaylist,
        renamePlaylist = actions.renamePlaylist,
        clearPlaylist = actions.clearPlaylist,
        deletePlaylist = actions.deletePlaylist,
        getFavoritesPlaylist = actions.getFavoritesPlaylist,
        sortPreferenceManager = sortPreferences,
        mediaImportObserver = FakeSongImportStateProvider(),
    )

    @Test
    fun `Favorites is pinned separately from the user's playlists`() = runTest {
        val favorites = createPlaylist(id = 1, name = "Favorites")
        val roadTrip = createPlaylist(id = 2, name = "Road trip")
        playlistRepository.favorites = favorites
        playlistRepository.setPlaylists(listOf(favorites, roadTrip))

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.favoritesPlaylist shouldBe favorites
        viewModel.uiState.value.playlists shouldBe listOf(roadTrip)
    }

    @Test
    fun `Favorites is pinned even before its first song, once created`() = runTest {
        val favorites = createPlaylist(id = 1, name = "Favorites")
        playlistRepository.favorites = favorites
        playlistRepository.setPlaylists(listOf(favorites))

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.favoritesPlaylist shouldBe favorites
        viewModel.uiState.value.playlists shouldBe emptyList()
    }
}
