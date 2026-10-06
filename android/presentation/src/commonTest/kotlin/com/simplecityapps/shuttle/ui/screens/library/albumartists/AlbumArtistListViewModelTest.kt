package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.createAlbumArtist
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.fakeLibraryViewPreferences
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.ArtistSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class AlbumArtistListViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val repository = FakeAlbumArtistRepository()
    private val sortPreferences = FakeSortPreferences()
    private val preferences = fakeLibraryViewPreferences(sort = sortPreferences)
    private val settingsStore = SettingsStore(InMemoryKeyValueStore())

    private val importState = FakeSongImportStateProvider()

    private fun viewModel() = AlbumArtistListViewModel(
        observeArtists = ObserveArtists(repository),
        observeSetting = ObserveSetting(settingsStore),
        readSetting = ReadLibraryViewSetting(preferences),
        saveSetting = SaveLibraryViewSetting(preferences),
        mediaImportObserver = importState,
    )

    @Test
    fun `the list holds album artists only until the setting adds those only credited on others' albums`() = runTest {
        repository.applyQueryPredicates = true
        repository.setAlbumArtists(
            listOf(
                createAlbumArtist(name = "Calvin Harris", albumCount = 1),
                createAlbumArtist(name = "Rihanna", albumCount = 0),
            )
        )
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.albumArtists.map { it.name } shouldBe listOf("Calvin Harris")

        SaveSetting(settingsStore)(ArtistSettings.ShowCreditedArtists, true)
        advanceUntilIdle()

        viewModel.uiState.value.albumArtists.map { it.name } shouldBe listOf("Calvin Harris", "Rihanna")
    }

    @Test
    fun `artists list by name with a letter index until another sort is chosen`() = runTest {
        repository.setAlbumArtists(
            listOf(
                createAlbumArtist(name = "Beta", albumCount = 5),
                createAlbumArtist(name = "Alpha", albumCount = 1),
                createAlbumArtist(name = "Gamma", albumCount = 3),
            )
        )
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.albumArtists.map { it.name } shouldBe listOf("Alpha", "Beta", "Gamma")
        viewModel.uiState.value.letterIndex shouldNotBe null
    }

    @Test
    fun `sorting by album count lists the most albums first drops the letter index and saves the choice`() = runTest {
        repository.setAlbumArtists(
            listOf(
                createAlbumArtist(name = "Beta", albumCount = 5),
                createAlbumArtist(name = "Alpha", albumCount = 1),
                createAlbumArtist(name = "Gamma", albumCount = 3),
                createAlbumArtist(name = "Delta", albumCount = 3),
            )
        )
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.setSortOrder(AlbumArtistSortOrder.AlbumCount)
        advanceUntilIdle()

        viewModel.uiState.value.albumArtists.map { it.name } shouldBe listOf("Beta", "Delta", "Gamma", "Alpha")
        viewModel.uiState.value.sortOrder shouldBe AlbumArtistSortOrder.AlbumCount
        viewModel.uiState.value.letterIndex shouldBe null
        sortPreferences.sortOrderArtistList shouldBe AlbumArtistSortOrder.AlbumCount
    }

    @Test
    fun `a saved sort order is the one the list starts in`() = runTest {
        sortPreferences.sortOrderArtistList = AlbumArtistSortOrder.PlayCount

        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.sortOrder shouldBe AlbumArtistSortOrder.PlayCount
    }

    @Test
    fun `an import in progress keeps the artists already imported`() = runTest {
        repository.setAlbumArtists(listOf(createAlbumArtist(name = "Kept")))
        importState.setState(SongImportState.ImportProgress(MediaProviderType.Jellyfin, null, Progress(1, 4)))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumArtistListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe Progress(1, 4)
        viewModel.uiState.value.albumArtists.map { it.name } shouldBe listOf("Kept")
    }
}
