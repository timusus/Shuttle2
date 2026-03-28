package com.simplecityapps.shuttle.ui.screens.library.songs

import android.app.Application
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.SongSortOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SongListViewModelTest {
    private val fakeSongRepository = FakeSongRepository()
    private val fakeImportStateProvider = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()

    // PlaybackManager/QueueManager are the playback boundary — still mocked
    // because they're concrete classes with deep dependency trees.
    // The state-derivation tests don't exercise them.
    private val mockPlaybackManager: PlaybackManager = mockk(relaxed = true)
    private val mockQueueManager: QueueManager = mockk(relaxed = true)
    private val mockApplication: Application = mockk()

    private lateinit var viewModel: SongListViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState initially emits Loading while songs are loading`() = runTest {
        val repo = FakeSongRepository()
        // Don't set songs — flow stays at null (never emits non-null)

        viewModel = createViewModel(songRepository = repo)

        viewModel.uiState.value.loadingState shouldBe SongListUiState.LoadingState.Loading

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe SongListUiState.LoadingState.Loading
    }

    @Test
    fun `uiState emits Scanning while media importer is scanning songs`() = runTest {
        fakeSongRepository.setSongs(emptyList())
        fakeImportStateProvider.setState(
            SongImportState.ImportProgress(MediaProviderType.Shuttle, null, IMPORT_PROGRESS)
        )

        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe SongListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe IMPORT_PROGRESS
    }

    @Test
    fun `uiState emits Ready with the list of songs, empty selection and sort order`() = runTest {
        fakeSongRepository.setSongs(listOf(SONG))
        fakeImportStateProvider.setState(importComplete())

        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.loadingState shouldBe SongListUiState.LoadingState.Ready
        state.songs shouldBe listOf(SONG)
        state.selectedSongs shouldBe emptySet()
        state.sortOrder shouldBe SongSortOrder.Default
    }

    @Test
    fun `starts selection on song long click`() = runTest {
        fakeSongRepository.setSongs(listOf(SONG))
        fakeImportStateProvider.setState(importComplete())
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.uiState.value.isSelecting shouldBe false

        viewModel.onSongLongClick(SONG)
        advanceUntilIdle()

        viewModel.uiState.value.isSelecting shouldBe true
        viewModel.uiState.value.selectedSongs shouldBe setOf(SONG)
    }

    @Test
    fun `adds song to selection when clicking it in selection mode`() = runTest {
        val songs = listOf(SONG1, SONG2)
        fakeSongRepository.setSongs(songs)
        fakeImportStateProvider.setState(importComplete())
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.onSongLongClick(SONG1)
        advanceUntilIdle()

        viewModel.onSongClick(SONG2)
        advanceUntilIdle()

        viewModel.uiState.value.isSelecting shouldBe true
        viewModel.uiState.value.selectedSongs shouldBe setOf(SONG1, SONG2)
    }

    @Test
    fun `removes selected song from selection`() = runTest {
        fakeSongRepository.setSongs(listOf(SONG1, SONG2))
        fakeImportStateProvider.setState(importComplete())
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.onSongLongClick(SONG1)
        advanceUntilIdle()
        viewModel.onSongClick(SONG2)
        advanceUntilIdle()

        viewModel.onSongClick(SONG1)
        advanceUntilIdle()

        viewModel.uiState.value.isSelecting shouldBe true
        viewModel.uiState.value.selectedSongs shouldBe setOf(SONG2)
    }

    @Test
    fun `exits selection mode when last selected song is clicked`() = runTest {
        fakeSongRepository.setSongs(listOf(SONG))
        fakeImportStateProvider.setState(importComplete())
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.onSongLongClick(SONG)
        advanceUntilIdle()

        viewModel.onSongClick(SONG)
        advanceUntilIdle()

        viewModel.uiState.value.isSelecting shouldBe false
        viewModel.uiState.value.selectedSongs shouldBe emptySet()
    }

    @Test
    fun `emits songs sorted in the sort order from the preferences`() = runTest {
        val song2 = createSong(name = "2")
        val song1 = createSong(name = "1")
        fakeSongRepository.setSongs(listOf(song2, song1))
        fakeSortPreferences.sortOrderSongList = SongSortOrder.SongName
        fakeImportStateProvider.setState(importComplete())

        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.sortOrder shouldBe SongSortOrder.SongName
        state.songs shouldBe listOf(song1, song2)
    }

    @Test
    fun `sets the sort order and persists to preferences`() = runTest {
        fakeSongRepository.setSongs(emptyList())
        fakeImportStateProvider.setState(importComplete())
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOrder(SongSortOrder.ArtistGroupKey)
        advanceUntilIdle()

        viewModel.uiState.value.sortOrder shouldBe SongSortOrder.ArtistGroupKey
        fakeSortPreferences.sortOrderSongList shouldBe SongSortOrder.ArtistGroupKey
    }

    private fun createViewModel(
        songRepository: FakeSongRepository = fakeSongRepository,
    ): SongListViewModel = SongListViewModel(
        songRepository = songRepository,
        playbackManager = mockPlaybackManager,
        queueManager = mockQueueManager,
        sortPreferenceManager = fakeSortPreferences,
        ioDispatcher = testDispatcher,
        mediaImportObserver = fakeImportStateProvider,
        application = mockApplication,
    )
}

private fun importComplete() = SongImportState.ImportComplete(MediaProviderType.Shuttle, null)

private val IMPORT_PROGRESS = Progress(0, 0)
private val SONG = createSong()
private val SONG1 = createSong(id = 1)
private val SONG2 = createSong(id = 2)
