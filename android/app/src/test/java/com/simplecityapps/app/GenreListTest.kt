package com.simplecityapps.app

import com.simplecityapps.mediaprovider.MediaImportObserver
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
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
    private val mediaProviders = listOf(MediaProviderType.MediaStore)

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

        every { sortPreferenceManager.sortOrderGenreList } returns GenreSortOrder.Default
        every { sortPreferenceManager.sortOrderGenreList = any() } just Runs

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
        // Arbitrary list
        val sortedGenres = listOf(
            Genre(name = "Classical", 3, 10, mediaProviders),
            Genre(name = "Electronic", 2, 10, mediaProviders),
            Genre(name = "Rock", 1, 10, mediaProviders)
        )
        // Mock the output of the repository, matching the mocked sortOrderGenreList value
        every { genreRepository.getGenres(match { it.sortOrder == GenreSortOrder.Default }) } returns flowOf(sortedGenres)

        val viewModel = GenreListViewModel(
            genreRepository,
            songRepository,
            playbackManager,
            queueManager,
            sortPreferenceManager,
            preferenceManager,
            mediaImportObserver
        )

        // Wait until ready, then capture state
        val result = viewModel.viewState.first { it is GenreListViewModel.ViewState.Ready }

        assertTrue(result is GenreListViewModel.ViewState.Ready)
        result as GenreListViewModel.ViewState.Ready
        // Assert that the view model state genres equal the repository output
        assertEquals(sortedGenres, result.genres)
    }

    @Test
    fun sortsByName() = runTest {
        val genres = listOf(
            Genre(name = "Pop", 1, 10, mediaProviders),
            Genre(name = "Ambient", 2, 10, mediaProviders),
            Genre(name = "Metal", 3, 10, mediaProviders)
        )
        every { genreRepository.getGenres(any()) } returns flowOf(genres)

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
        assertEquals(
            listOf(
                Genre(name = "Ambient", 2, 10, mediaProviders),
                Genre(name = "Metal", 3, 10, mediaProviders),
                Genre(name = "Pop", 1, 10, mediaProviders)
            ),
            result.genres
        )
    }

    @Test
    fun sortsBySongCount() = runTest {
        val genres = listOf(
            Genre(name = "Pop", 1, 10, mediaProviders),
            Genre(name = "Ambient", 2, 10, mediaProviders),
            Genre(name = "Metal", 3, 10, mediaProviders)
        )
        every { genreRepository.getGenres(any()) } returns flowOf(genres)

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

        viewModel.setSortOrder(GenreSortOrder.SongCount)

        // Capture next emitted state
        val result = viewModel.viewState.drop(1).first()

        assertTrue(result is GenreListViewModel.ViewState.Ready)
        result as GenreListViewModel.ViewState.Ready
        assertEquals(
            listOf(
                Genre(name = "Metal", 3, 10, mediaProviders),
                Genre(name = "Ambient", 2, 10, mediaProviders),
                Genre(name = "Pop", 1, 10, mediaProviders)
            ),
            result.genres
        )
    }
}
