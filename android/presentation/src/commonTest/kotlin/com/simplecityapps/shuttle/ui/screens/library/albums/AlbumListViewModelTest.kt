package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumListPreferences
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.fakeLibraryViewPreferences
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Focused ViewModel unit tests for behaviour that can't be observed through the UI, notably
 * [AlbumListViewModel.onShuffle]'s side effects. State derivation and selection are tested via
 * the app's Compose characterisation tests (real ViewModel + real Composable + fakes).
 */
class AlbumListViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()
    private val fakeViewModePreferences = FakeAlbumListPreferences()
    private val fakeQueueOperations = FakeQueueOperations()
    private val fakePlaybackOperations = FakePlaybackOperations()

    @Test
    fun `onShuffle queues each album's songs together in track order`() = runTest {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Side A Track 2", album = "Side A", track = 2),
                createSong(id = 2, name = "Side B Track 1", album = "Side B", track = 1),
                createSong(id = 3, name = "Side A Track 1", album = "Side A", track = 1),
                createSong(id = 4, name = "Side B Track 2", album = "Side B", track = 2),
            )
        )
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.onShuffle()
        advanceUntilIdle()

        val queuedNames = fakeQueueOperations.lastSetQueue.orEmpty().map { it.name }
        val possibleOrders = listOf(
            listOf("Side A Track 1", "Side A Track 2", "Side B Track 1", "Side B Track 2"),
            listOf("Side B Track 1", "Side B Track 2", "Side A Track 1", "Side A Track 2"),
        )
        (queuedNames in possibleOrders) shouldBe true
    }

    @Test
    fun `onShuffle keeps same-titled albums by different artists as separate units`() = runTest {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Artist A Track 2", albumArtist = "Artist A", album = "Greatest Hits", track = 2),
                createSong(id = 2, name = "Artist B Track 1", albumArtist = "Artist B", album = "Greatest Hits", track = 1),
                createSong(id = 3, name = "Artist A Track 1", albumArtist = "Artist A", album = "Greatest Hits", track = 1),
                createSong(id = 4, name = "Artist B Track 2", albumArtist = "Artist B", album = "Greatest Hits", track = 2),
            )
        )
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.onShuffle()
        advanceUntilIdle()

        val queuedNames = fakeQueueOperations.lastSetQueue.orEmpty().map { it.name }
        val possibleOrders = listOf(
            listOf("Artist A Track 1", "Artist A Track 2", "Artist B Track 1", "Artist B Track 2"),
            listOf("Artist B Track 1", "Artist B Track 2", "Artist A Track 1", "Artist A Track 2"),
        )
        (queuedNames in possibleOrders) shouldBe true
    }

    @Test
    fun `onShuffle does not enable shuffle mode`() = runTest {
        fakeSongRepository.setSongs(listOf(createSong(id = 1, name = "Solo", album = "Only Album")))
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.onShuffle()
        advanceUntilIdle()

        fakeQueueOperations.shuffleModeFlow.value shouldBe ShuffleMode.Off
    }

    @Test
    fun `a failed shuffle holds a message until the screen consumes it`() = runTest {
        fakeSongRepository.setSongs(listOf(createSong(id = 1, name = "Solo", album = "Only Album")))
        fakeImportState.setState(importComplete())
        fakePlaybackOperations.loadResult = Result.failure(IllegalStateException("File not found"))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onShuffle()
        advanceUntilIdle()

        val event = viewModel.uiState.value.events.single()
        event.value shouldBe AlbumListEvent.ShuffleFailed("File not found")

        viewModel.onEventHandled(event.id)
        advanceUntilIdle()

        viewModel.uiState.value.events shouldBe emptyList()
    }

    @Test
    fun `sorting by date added lists the most recently added album first and saves the choice`() = runTest {
        fakeAlbumRepository.setAlbums(
            listOf(
                createAlbum(name = "Old", dateAdded = Instant.fromEpochSeconds(100)),
                createAlbum(name = "New", dateAdded = Instant.fromEpochSeconds(300)),
                createAlbum(name = "Undated", dateAdded = null),
                createAlbum(name = "Mid", dateAdded = Instant.fromEpochSeconds(200)),
            )
        )
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.setSortOrder(AlbumSortOrder.DateAdded)
        advanceUntilIdle()

        viewModel.uiState.value.albums.map { it.name } shouldBe listOf("New", "Mid", "Old", "Undated")
        viewModel.uiState.value.letterIndex shouldBe null
        fakeSortPreferences.sortOrderAlbumList shouldBe AlbumSortOrder.DateAdded
    }

    @Test
    fun `an import in progress keeps the albums already imported`() = runTest {
        val album = createAlbum(name = "Kept")
        fakeAlbumRepository.setAlbums(listOf(album))
        fakeImportState.setState(SongImportState.ImportProgress(MediaProviderType.Jellyfin, null, Progress(1, 4)))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe Progress(1, 4)
        viewModel.uiState.value.albums.map { it.name } shouldBe listOf("Kept")
    }

    private fun createViewModel(random: Random = Random.Default): AlbumListViewModel {
        val preferences = fakeLibraryViewPreferences(sort = fakeSortPreferences, albumList = fakeViewModePreferences)
        val testMediaActions = TestMediaActions(
            fakeSongRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            fakeQueueOperations,
            playbackOperations = fakePlaybackOperations,
            albumRepository = fakeAlbumRepository,
        )
        return AlbumListViewModel(
            observeAlbums = testMediaActions.observeAlbums,
            observeSongs = testMediaActions.observeSongs,
            shuffleAlbums = ShuffleAlbums(fakeQueueOperations, fakePlaybackOperations),
            readSetting = ReadLibraryViewSetting(preferences),
            saveSetting = SaveLibraryViewSetting(preferences),
            mediaImportObserver = fakeImportState,
            random = random,
        )
    }
}
