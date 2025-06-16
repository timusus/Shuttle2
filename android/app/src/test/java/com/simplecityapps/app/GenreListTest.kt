package com.simplecityapps.app

import com.simplecityapps.mediaprovider.MediaImportObserver
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenreListTest {
    private val testDispatcher = StandardTestDispatcher()

    private val genreRepository = mockk<GenreRepository>()
    private val songRepository = mockk<SongRepository>()
    private val playbackManager = mockk<PlaybackManager>()
    private val queueManager = mockk<QueueManager>()
    private val sortPreferenceManager = mockk<SortPreferenceManager>()
    private val preferenceManager = mockk<GeneralPreferenceManager>()
    private val mediaImportObserver = mockk<MediaImportObserver>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { mediaImportObserver.songImportState } returns
            MutableStateFlow(SongImportState.ImportComplete(MediaProviderType.MediaStore, ""))
        every { preferenceManager.theme(any()) } returns MutableStateFlow(GeneralPreferenceManager.Theme.Dark)
        every { preferenceManager.accent(any()) } returns MutableStateFlow(GeneralPreferenceManager.Accent.Default)
        every { preferenceManager.extraDark(any()) } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sortsOnInitPerPreferences() = runTest {
        val sortedGenres = generateGenres()
        // Mock the output of the repository, matching the mocked sortOrderGenreList value
        every { genreRepository.getGenres(match { it.sortOrder == GenreSortOrder.Default }) } returns flowOf(sortedGenres)
        every { sortPreferenceManager.sortOrderGenreList } returns GenreSortOrder.Default

        val viewModel = GenreListViewModel(
            genreRepository,
            songRepository,
            playbackManager,
            queueManager,
            sortPreferenceManager,
            preferenceManager,
            mediaImportObserver
        )

        // Capture ready state
        val result = viewModel.viewState.first { it is GenreListViewModel.ViewState.Ready }

        assertTrue(result is GenreListViewModel.ViewState.Ready)
        result as GenreListViewModel.ViewState.Ready
        // Assert that the view model state genres equal the repository output
        assertEquals(sortedGenres, result.genres)
    }

    @Test
    fun setsSortOrderIfReady() = runTest {
        val genres = generateGenres()
        every { genreRepository.getGenres(any()) } returns flowOf(genres)
        every { sortPreferenceManager.sortOrderGenreList } returns GenreSortOrder.Default
        every { sortPreferenceManager.sortOrderGenreList = any() } just Runs

        val viewModel = GenreListViewModel(
            genreRepository,
            songRepository,
            playbackManager,
            queueManager,
            sortPreferenceManager,
            preferenceManager,
            mediaImportObserver
        )

        // Wait until ready
        viewModel.viewState.first { it is GenreListViewModel.ViewState.Ready }

        viewModel.setSortOrder(GenreSortOrder.Name)

        // Capture next emitted state
        val result = viewModel.viewState.drop(1).first()

        assertTrue(result is GenreListViewModel.ViewState.Ready)
        result as GenreListViewModel.ViewState.Ready
        assertEquals(genres.sortedWith(GenreSortOrder.Name.comparator), result.genres)
    }

    @Test
    fun skipsRedundantSort() = runTest {
        every { genreRepository.getGenres(any()) } returns flowOf(generateGenres())
        every { sortPreferenceManager.sortOrderGenreList } returns GenreSortOrder.Default

        val viewModel = GenreListViewModel(
            genreRepository,
            songRepository,
            playbackManager,
            queueManager,
            sortPreferenceManager,
            preferenceManager,
            mediaImportObserver
        )

        // Wait until ready
        viewModel.viewState.first { it is GenreListViewModel.ViewState.Ready }

        viewModel.setSortOrder(GenreSortOrder.Default)

        verify(exactly = 0) { sortPreferenceManager.sortOrderGenreList = any() }
    }

    private fun generateGenres(count: Int = 5): List<Genre> = List(count) { index ->
        Genre(
            name = "Genre $index",
            songCount = (5..20).random(),
            duration = (10..100).random(),
            mediaProviders = listOf(MediaProviderType.MediaStore)
        )
    }
}
