package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.fakeLibraryViewPreferences
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistCovers
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistId
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playlistRepository = FakePlaylistRepository().apply { favorites = createPlaylist(id = 99, name = "Favorites") }
    private val importState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()
    private val preferences = fakeLibraryViewPreferences(sort = fakeSortPreferences)
    private val actions = TestMediaActions(playlistRepository = playlistRepository)

    private fun viewModel(): PlaylistListViewModel = PlaylistListViewModel(
        observePlaylists = actions.observePlaylists,
        createPlaylist = actions.createPlaylist,
        renamePlaylist = actions.renamePlaylist,
        clearPlaylist = actions.clearPlaylist,
        deletePlaylist = actions.deletePlaylist,
        getFavoritesPlaylist = actions.getFavoritesPlaylist,
        readSetting = ReadLibraryViewSetting(preferences),
        saveSetting = SaveLibraryViewSetting(preferences),
        mediaImportObserver = importState,
        observePlaylistCovers = ObservePlaylistCovers(actions.observePlaylistSongs),
    )

    @Test
    fun `each playlist's covers are its first four songs from different albums (#491)`() = runTest {
        val roadTrip = createPlaylist(id = 2, name = "Road trip")
        val songs = listOf("A", "A", "B", "C", "D", "E").mapIndexed { i, album -> createSong(id = i.toLong(), album = album) }
        playlistRepository.setPlaylists(listOf(roadTrip))
        playlistRepository.setSongsForPlaylist(roadTrip, songs)

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.covers[roadTrip.id]?.map { it.album } shouldBe listOf("A", "B", "C", "D")
    }

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

    @Test
    fun `shows scanning state with progress while import is in progress`() = runTest {
        importState.setState(SongImportState.ImportProgress(MediaProviderType.Shuttle, null, Progress(50, 200)))

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe PlaylistListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe Progress(50, 200)
    }

    @Test
    fun `smart playlists are always present, even with no user playlists`() = runTest {
        playlistRepository.setPlaylists(emptyList())
        importState.setState(importComplete())

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.smartPlaylists shouldBe SmartPlaylistId.entries.map { it.smartPlaylist }
    }

    @Test
    fun `sorts playlists by name case-insensitively when Name sort order is selected`() = runTest {
        playlistRepository.setPlaylists(
            listOf(
                createPlaylist(name = "zebra"),
                createPlaylist(name = "Apple"),
                createPlaylist(name = "banana"),
            )
        )
        importState.setState(importComplete())
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOrder(PlaylistSortOrder.Name)
        advanceUntilIdle()

        viewModel.uiState.value.playlists.map { it.name } shouldBe listOf("Apple", "banana", "zebra")
    }

    @Test
    fun `setSortOrder persists to preferences`() = runTest {
        importState.setState(importComplete())
        val viewModel = viewModel()

        viewModel.setSortOrder(PlaylistSortOrder.Name)

        fakeSortPreferences.sortOrderPlaylistList shouldBe PlaylistSortOrder.Name
    }
}
