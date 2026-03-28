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
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@ExperimentalCoroutinesApi
class SongListViewModelTest {
    val mockSongRepository: SongRepository = mockk()
    val mockPlaybackManager: PlaybackManager = mockk()
    val mockQueueManager: QueueManager = mockk()
    val mockSortPreferenceManager: SortPreferenceManager = mockk()
    val mockPreferenceManager: GeneralPreferenceManager = mockk(relaxed = true)
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
    fun `viewState initially emits Loading while songs are loading`() = runTest {
        // Arrange
        every { mockSongRepository.getSongs(any()) } returns
            neverEmittingFlow()

        // Act: Initialize the ViewModel. The `init` block will execute.
        viewModel = createViewModel()

        // Assert: before the flows are consumed
        viewModel.viewState.value shouldBe SongListViewModel.ViewState.Loading

        // Act: Wait for the flows
        advanceUntilIdle()

        // Assert: after the flows are consumed
        viewModel.viewState.value shouldBe SongListViewModel.ViewState.Loading
    }

    @Test
    fun `viewState emits Scanning while media importer is scanning songs`() = runTest {
        mockSongs(emptyList())
        mockSongImportStateAsImportProgress(IMPORT_PROGRESS)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.viewState.value shouldBe
            SongListViewModel.ViewState.Scanning(IMPORT_PROGRESS)
    }

    @Test
    fun `viewState emits Ready with the list of songs, empty selection and sort order`() = runTest {
        mockSongs(listOf(SONG))
        mockSortOrderPreference(SongSortOrder.Default)
        mockSongImportStateAsImportComplete()

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.viewState.value shouldBe
            SongListViewModel.ViewState.Ready(
                listOf(SONG),
                emptySet(),
                SongSortOrder.Default
            )
    }

    @Test
    fun `plays song when clicked and adds all songs to the queue`() = runTest {
        val songs = listOf(SONG1, SONG2)
        mockSongs(songs)
        mockSongImportStateAsImportComplete()
        coEvery { mockQueueManager.setQueue(allAny()) } returns
            true
        // FIXME: This is too fragile. Implement PlaybackManager.loadAndPlay?
        coEvery { mockPlaybackManager.load(seekPosition = null, completion = any()) } answers {
            (arg(1) as (Result<Boolean>) -> Unit).invoke(Result.success(true))
        }
        every { mockPlaybackManager.play() } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSongClick(SONG2) {}
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockQueueManager.setQueue(songs = songs, position = 1)
            mockPlaybackManager.load(completion = any())
            mockPlaybackManager.play()
        }
    }

    @Test
    fun `plays song when clicked and just adds it to the queue when import hasn't finished`() = runTest {
        val songs = listOf(SONG1, SONG2)
        mockSongs(songs)
        mockSongImportStateAsImportProgress()
        coEvery { mockQueueManager.setQueue(allAny()) } returns
            true
        // FIXME: This is too fragile. Implement PlaybackManager.loadAndPlay?
        coEvery { mockPlaybackManager.load(seekPosition = null, completion = any()) } answers {
            (arg(1) as (Result<Boolean>) -> Unit).invoke(Result.success(true))
        }
        every { mockPlaybackManager.play() } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSongClick(SONG2) {}
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockQueueManager.setQueue(songs = listOf(SONG2), position = 0)
            mockPlaybackManager.load(completion = any())
            mockPlaybackManager.play()
        }
    }

    @Test
    fun `starts selection on song long click`() = runTest {
        mockSongs(listOf(SONG))
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.contextualToolbarHelper.isSelecting().shouldBeFalse()

        viewModel.onSongLongClick(SONG)

        viewModel.contextualToolbarHelper.isSelecting().shouldBeTrue()
        viewModel.contextualToolbarHelper.selectedSongsState.value
            .shouldBe(listOf(SONG))
    }

    @Test
    fun `adds song to selection when clicking it in selection mode`() = runTest {
        val songs = listOf(SONG1, SONG2)
        mockSongs(songs)
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSongLongClick(SONG1)

        viewModel.onSongClick(SONG2) {}

        viewModel.contextualToolbarHelper.isSelecting().shouldBeTrue()
        viewModel.contextualToolbarHelper.selectedSongsState.value
            .shouldBe(songs)
    }

    @Test
    fun `removes selected song from selection`() = runTest {
        val songs = listOf(SONG1, SONG2)
        mockSongs(songs)
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSongLongClick(SONG1)
        viewModel.onSongClick(SONG2) {}

        viewModel.onSongClick(SONG1) {}

        viewModel.contextualToolbarHelper.isSelecting().shouldBeTrue()
        viewModel.contextualToolbarHelper.selectedSongsState.value
            .shouldBe(listOf(SONG2))
    }

    @Test
    fun `exists selection mode when last selected song is clicked`() = runTest {
        mockSongs(listOf(SONG))
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.contextualToolbarHelper.isSelecting().shouldBeFalse()
        viewModel.onSongLongClick(SONG)

        viewModel.onSongClick(SONG) {}

        viewModel.contextualToolbarHelper.isSelecting().shouldBeFalse()
        viewModel.contextualToolbarHelper.selectedSongsState.value
            .shouldBe(emptyList())
    }

    @Test
    fun `adds selected songs to queue`() = runTest {
        val songs = listOf(SONG1, SONG2)
        mockSongs(songs)
        coEvery { mockPlaybackManager.addToQueue(allAny()) } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSongLongClick(SONG2)

        viewModel.addSelectedToQueue()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockPlaybackManager.addToQueue(listOf(SONG2)) }
        viewModel.contextualToolbarHelper.isSelecting().shouldBeFalse()
    }

    @Test
    fun `shuffles songs`() = runTest {
        mockSongs(listOf(SONG))
        // FIXME: This is too fragile. Implement PlaybackManager.shuffleAndPlay?
        coEvery { mockPlaybackManager.shuffle(songs = any(), completion = any()) } answers {
            (arg(1) as (Result<Boolean>) -> Unit).invoke(Result.success(true))
        }
        every { mockPlaybackManager.play() } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.shuffle {}
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockPlaybackManager.shuffle(songs = listOf(SONG), any())
            mockPlaybackManager.play()
        }
    }

    @Test
    fun `fails to shuffle when there aren't any songs`() = runTest {
        var shuffleFailure = false
        mockSongs(emptyList())
        // FIXME: This is too fragile. Implement PlaybackManager.shuffleAndPlay?
        coEvery { mockPlaybackManager.shuffle(songs = any(), completion = any()) } answers {
            (arg(1) as (Result<Boolean>) -> Unit).invoke(Result.success(true))
        }
        every { mockPlaybackManager.play() } just Runs
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.shuffle { shuffleFailure = true }
        advanceUntilIdle()

        shuffleFailure.shouldBeTrue()
        coVerify(exactly = 0) {
            mockPlaybackManager.shuffle(songs = any(), any())
            mockPlaybackManager.play()
        }
    }

    @Test
    fun `emits songs sorted in the sort order from the preferences`() = runTest {
        val song2 = createSong(name = "2")
        val song1 = createSong(name = "1")
        val songs = listOf(song2, song1)
        mockSongs(songs)
        mockSortOrderPreference(SongSortOrder.SongName)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectedSortOrder.value.shouldBe(SongSortOrder.SongName)
        viewModel.viewState.value shouldBeEqualUsingFields
            SongListViewModel.ViewState.Ready(
                songs.reversed(),
                emptySet(),
                SongSortOrder.SongName,
            )
    }

    @Test
    @Ignore(
        """Fails due to running in IO dispatcher. Fix by injecting
        StandardTestDispatcher when creating the view model."""
    )
    fun `sets the sort order`() = runTest {
        val spiedSortPreferenceManager = spyk(
            SortPreferenceManager(mockk(relaxed = true))
        )
        viewModel = SongListViewModel(
            songRepository = mockSongRepository,
            playbackManager = mockPlaybackManager,
            queueManager = mockQueueManager,
            sortPreferenceManager = spiedSortPreferenceManager,
            preferenceManager = mockPreferenceManager,
            mediaImportObserver = mockMediaImportObserver,
            application = mockApplication,
        )
        advanceUntilIdle()

        viewModel.setSortOrder(SongSortOrder.ArtistGroupKey)
        advanceUntilIdle()

        viewModel.selectedSortOrder.value.shouldBe(SongSortOrder.ArtistGroupKey)
        io.mockk.verify { spiedSortPreferenceManager.sortOrderSongList = SongSortOrder.ArtistGroupKey }
    }

    @Test
    fun `reports play failure via completion callback`() = runTest {
        val playError = Error("playback failed")
        mockSongs(listOf(SONG))
        mockSongImportStateAsImportComplete()
        coEvery { mockQueueManager.setQueue(allAny()) } returns true
        coEvery { mockPlaybackManager.load(seekPosition = null, completion = any()) } answers {
            (arg(1) as (Result<Boolean>) -> Unit).invoke(Result.failure(playError))
        }
        viewModel = createViewModel()
        advanceUntilIdle()

        var receivedError: Throwable? = null
        viewModel.onSongClick(SONG) { result ->
            result.onFailure { receivedError = it }
        }
        advanceUntilIdle()

        receivedError.shouldBe(playError)
    }

    fun createViewModel(): SongListViewModel = SongListViewModel(
        songRepository = mockSongRepository,
        playbackManager = mockPlaybackManager,
        queueManager = mockQueueManager,
        sortPreferenceManager = mockSortPreferenceManager,
        preferenceManager = mockPreferenceManager,
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
