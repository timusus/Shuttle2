package com.simplecityapps.shuttle.ui.screens.search

import android.content.Context
import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var preferenceManager: GeneralPreferenceManager
    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()

    private val airbag = createSong(id = 1, name = "Airbag", albumArtist = "Radiohead", album = "OK Computer")
    private val paranoid = createSong(id = 2, name = "Paranoid Android", albumArtist = "Radiohead", album = "OK Computer")

    @Before
    fun setUp() {
        preferenceManager = GeneralPreferenceManager(context.getSharedPreferences("search-test", Context.MODE_PRIVATE).apply { edit().clear().commit() })
        songs.setSongs(listOf(airbag, paranoid))
        albums.setAlbums(listOf(createAlbum("OK Computer", "Radiohead")))
    }

    private fun TestScope.viewModel(): SearchViewModel {
        val dispatcher = mainDispatcherRule.testDispatcher
        val index = LibrarySearchIndex(FakeAlbumArtistRepository(), albums, songs, FakeGenreRepository(), FakePlaylistRepository(), backgroundScope, dispatcher)
        val searchLibrary = SearchLibrary(index, dispatcher)
        return SearchViewModel(searchLibrary, RecentSearches(preferenceManager), preferenceManager).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
        }
    }

    private fun TestScope.type(viewModel: SearchViewModel, query: String) {
        viewModel.onQueryChange(query)
        advanceTimeBy(SearchViewModel.SearchDebounce.inWholeMilliseconds + 1)
        runCurrent()
    }

    @Test
    fun `an empty query shows the stored recent searches`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.recentSearches = listOf("bjork", "air")

        viewModel().uiState.value.content shouldBe SearchContent.Recent(listOf("bjork", "air"))
    }

    @Test
    fun `typing searches once the debounce has passed`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onQueryChange("airbag")
        advanceTimeBy(SearchViewModel.SearchDebounce.inWholeMilliseconds - 1)
        runCurrent()
        viewModel.uiState.value.content.shouldBeInstanceOf<SearchContent.Recent>()

        advanceTimeBy(101)
        runCurrent()
        val content = viewModel.uiState.value.content.shouldBeInstanceOf<SearchContent.Results>()
        content.query shouldBe "airbag"
        content.results.songs.first().item shouldBe airbag
    }

    @Test
    fun `a query that matches nothing says so`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        type(viewModel, "zzzzzz")

        viewModel.uiState.value.content shouldBe SearchContent.NoResults("zzzzzz")
    }

    @Test
    fun `clearing the query goes back to the recent searches`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        type(viewModel, "airbag")

        viewModel.onQueryChange("")
        runCurrent()

        viewModel.uiState.value.content shouldBe SearchContent.Recent(emptyList())
    }

    @Test
    fun `toggling a category filters the results and is remembered`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        type(viewModel, "radiohead")

        viewModel.onToggleCategory(SearchCategory.Songs)
        advanceTimeBy(SearchViewModel.SearchDebounce.inWholeMilliseconds + 1)
        runCurrent()

        val content = viewModel.uiState.value.content.shouldBeInstanceOf<SearchContent.Results>()
        content.results.songs shouldBe emptyList()
        content.results.albums.map { it.item.name } shouldBe listOf("OK Computer")
        (SearchCategory.Songs in viewModel.uiState.value.categories) shouldBe false
        preferenceManager.searchFilterSongs shouldBe false
    }

    @Test
    fun `submitting a query records it as a recent search`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.recentSearches = listOf("air")
        val viewModel = viewModel()
        type(viewModel, "Radiohead")

        viewModel.onSearch()
        viewModel.onQueryChange("")
        runCurrent()

        viewModel.uiState.value.content shouldBe SearchContent.Recent(listOf("Radiohead", "air"))
        preferenceManager.recentSearches shouldBe listOf("Radiohead", "air")
    }

    @Test
    fun `removing a recent search forgets it`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.recentSearches = listOf("bjork", "air")
        val viewModel = viewModel()

        viewModel.onRemoveRecentSearch("bjork")
        runCurrent()

        viewModel.uiState.value.content shouldBe SearchContent.Recent(listOf("air"))
    }

    @Test
    fun `tapping a song plays every song result from that one`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        type(viewModel, "radiohead")
        val results = (viewModel.uiState.value.content as SearchContent.Results).results.songs.map { it.item }

        viewModel.playSong(1) shouldBe MediaAction.Play(MediaSelection.Songs(results), position = 1)
        preferenceManager.recentSearches shouldBe listOf("radiohead")
    }
}
