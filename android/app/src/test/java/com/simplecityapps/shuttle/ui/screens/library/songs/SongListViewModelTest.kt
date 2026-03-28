package com.simplecityapps.shuttle.ui.screens.library.songs

import android.app.Application
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.MediaImportObserver
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.neverEmittingFlow
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
    val mockSongRepository: SongRepository = mockk()
    val mockPlaybackManager: PlaybackManager = mockk()
    val mockQueueManager: QueueManager = mockk()
    val mockSortPreferenceManager: SortPreferenceManager = mockk()
    val mockMediaImportObserver: MediaImportObserver = mockk()
    val mockApplication: Application = mockk()

    lateinit var viewModel: SongListViewModel

    val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockSortOrderPreference(SongSortOrder.Default)
        mockSongs(emptyList())
        mockSongImportState(SongImportState.Idle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState initially emits Loading while songs are loading`() = runTest {
        every { mockSongRepository.getSongs(any()) } returns
            neverEmittingFlow()

        viewModel = createViewModel()

        viewModel.uiState.value.loadingState shouldBe SongListUiState.LoadingState.Loading

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe SongListUiState.LoadingState.Loading
    }

    @Test
    fun `uiState emits Scanning while media importer is scanning songs`() = runTest {
        mockSongs(emptyList())
        mockSongImportStateAsImportProgress(IMPORT_PROGRESS)

        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe SongListUiState.LoadingState.Scanning
        viewModel.uiState.value.scanProgress shouldBe IMPORT_PROGRESS
    }

    @Test
    fun `uiState emits Ready with the list of songs, empty selection and sort order`() = runTest {
        mockSongs(listOf(SONG))
        mockSortOrderPreference(SongSortOrder.Default)
        mockSongImportStateAsImportComplete()

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
        mockSongs(listOf(SONG))
        mockSongImportStateAsImportComplete()
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
        mockSongs(songs)
        mockSongImportStateAsImportComplete()
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
        val songs = listOf(SONG1, SONG2)
        mockSongs(songs)
        mockSongImportStateAsImportComplete()
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
        mockSongs(listOf(SONG))
        mockSongImportStateAsImportComplete()
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.uiState.value.isSelecting shouldBe false
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
        val songs = listOf(song2, song1)
        mockSongs(songs)
        mockSortOrderPreference(SongSortOrder.SongName)
        mockSongImportStateAsImportComplete()

        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.sortOrder shouldBe SongSortOrder.SongName
        state.songs shouldBe songs.reversed()
    }

    @Test
    fun `sets the sort order`() = runTest {
        mockSongImportStateAsImportComplete()
        every { mockSortPreferenceManager.sortOrderSongList = any() } just Runs
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSortOrder(SongSortOrder.ArtistGroupKey)
        advanceUntilIdle()

        viewModel.uiState.value.sortOrder shouldBe SongSortOrder.ArtistGroupKey
        io.mockk.verify { mockSortPreferenceManager.sortOrderSongList = SongSortOrder.ArtistGroupKey }
    }

    fun createViewModel(): SongListViewModel = SongListViewModel(
        songRepository = mockSongRepository,
        playbackManager = mockPlaybackManager,
        queueManager = mockQueueManager,
        sortPreferenceManager = mockSortPreferenceManager,
        ioDispatcher = testDispatcher,
        mediaImportObserver = mockMediaImportObserver,
        application = mockApplication,
    )

    fun mockSongs(songs: List<Song>) = every { mockSongRepository.getSongs(any()) } returns
        flowOf(songs)

    fun mockSongImportStateAsImportComplete() {
        mockSongImportState(
            SongImportState.ImportComplete(
                MediaProviderType.Shuttle,
                null,
            )
        )
    }

    private fun mockSongImportStateAsImportProgress(importProgress: Progress? = null) {
        mockSongImportState(
            SongImportState.ImportProgress(
                MediaProviderType.Shuttle,
                null,
                importProgress,
            )
        )
    }

    fun mockSongImportState(state: SongImportState) = every { mockMediaImportObserver.songImportState } returns
        MutableStateFlow(state)

    fun mockSortOrderPreference(sortOrder: SongSortOrder) = every { mockSortPreferenceManager.sortOrderSongList } returns
        sortOrder
}

val IMPORT_PROGRESS = Progress(0, 0)
val SONG = createSong()
val SONG1 = createSong(id = 1)
val SONG2 = createSong(id = 2)
