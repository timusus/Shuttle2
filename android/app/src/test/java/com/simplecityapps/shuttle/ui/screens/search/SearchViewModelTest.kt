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

    private val chlorophyllLoop = createSong(id = 1, name = "Chlorophyll Loop", albumArtist = "Juniper Static", album = "Phase Garden")
    private val petalArithmetic = createSong(id = 2, name = "Petal Arithmetic", albumArtist = "Juniper Static", album = "Phase Garden")

    @Before
    fun setUp() {
        preferenceManager = GeneralPreferenceManager(context.getSharedPreferences("search-test", Context.MODE_PRIVATE).apply { edit().clear().commit() })
        songs.setSongs(listOf(chlorophyllLoop, petalArithmetic))
        albums.setAlbums(listOf(createAlbum("Phase Garden", "Juniper Static")))
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

    /** Every content the screen is shown from now on, starting with the current one. */
    private fun TestScope.recordContent(viewModel: SearchViewModel): List<SearchContent> {
        val shown = mutableListOf<SearchContent>()
        backgroundScope.launch { viewModel.uiState.collect { shown += it.content } }
        runCurrent()
        return shown
    }

    private fun TestScope.type(viewModel: SearchViewModel, query: String) {
        viewModel.onQueryChange(query)
        advanceTimeBy(SearchViewModel.SearchDebounce.inWholeMilliseconds + 1)
        runCurrent()
    }

    @Test
    fun `an empty query shows the stored recent searches`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.recentSearches = listOf("kestrel", "salt")

        viewModel().uiState.value.content shouldBe SearchContent.Recent(listOf("kestrel", "salt"))
    }

    @Test
    fun `typing searches once the debounce has passed`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()

        viewModel.onQueryChange("chlorophyll")
        advanceTimeBy(SearchViewModel.SearchDebounce.inWholeMilliseconds - 1)
        runCurrent()
        viewModel.uiState.value.content.shouldBeInstanceOf<SearchContent.Recent>()

        advanceTimeBy(101)
        runCurrent()
        val content = viewModel.uiState.value.content.shouldBeInstanceOf<SearchContent.Results>()
        content.query shouldBe "chlorophyll"
        content.results.songs.first().item shouldBe chlorophyllLoop
    }

    @Test
    fun `the first query shows the searching state until its results arrive`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        val shown = recordContent(viewModel)

        type(viewModel, "chlorophyll")

        shown.drop(1).first() shouldBe SearchContent.Searching
        shown.last().shouldBeInstanceOf<SearchContent.Results>().query shouldBe "chlorophyll"
    }

    @Test
    fun `the previous results stay up while the next query runs`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        type(viewModel, "chlorophyll")
        val shown = recordContent(viewModel)

        type(viewModel, "petal")

        shown.first().shouldBeInstanceOf<SearchContent.Results>().query shouldBe "chlorophyll"
        shown.none { it == SearchContent.Searching } shouldBe true
        val content = shown.last().shouldBeInstanceOf<SearchContent.Results>()
        content.query shouldBe "petal"
        content.results.songs.first().item shouldBe petalArithmetic
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
        type(viewModel, "chlorophyll")

        viewModel.onQueryChange("")
        runCurrent()

        viewModel.uiState.value.content shouldBe SearchContent.Recent(emptyList())
    }

    @Test
    fun `toggling a category filters the results and is remembered`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        type(viewModel, "juniper")

        viewModel.onToggleCategory(SearchCategory.Songs)
        advanceTimeBy(SearchViewModel.SearchDebounce.inWholeMilliseconds + 1)
        runCurrent()

        val content = viewModel.uiState.value.content.shouldBeInstanceOf<SearchContent.Results>()
        content.results.songs shouldBe emptyList()
        content.results.albums.map { it.item.name } shouldBe listOf("Phase Garden")
        (SearchCategory.Songs in viewModel.uiState.value.categories) shouldBe false
        preferenceManager.searchFilterSongs shouldBe false
    }

    @Test
    fun `a new search starts with the categories left off last time`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel().onToggleCategory(SearchCategory.Songs)
        runCurrent()

        val reopened = viewModel()
        type(reopened, "juniper")

        (SearchCategory.Songs in reopened.uiState.value.categories) shouldBe false
        reopened.uiState.value.content.shouldBeInstanceOf<SearchContent.Results>().results.songs shouldBe emptyList()
    }

    @Test
    fun `submitting a query records it as a recent search`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.recentSearches = listOf("salt")
        val viewModel = viewModel()
        type(viewModel, "Juniper")

        viewModel.onSearch()
        viewModel.onQueryChange("")
        runCurrent()

        viewModel.uiState.value.content shouldBe SearchContent.Recent(listOf("Juniper", "salt"))
        preferenceManager.recentSearches shouldBe listOf("Juniper", "salt")
    }

    @Test
    fun `removing a recent search forgets it`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.recentSearches = listOf("kestrel", "salt")
        val viewModel = viewModel()

        viewModel.onRemoveRecentSearch("kestrel")
        runCurrent()

        viewModel.uiState.value.content shouldBe SearchContent.Recent(listOf("salt"))
    }

    @Test
    fun `tapping a song plays every song result from that one`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        type(viewModel, "juniper")
        val results = (viewModel.uiState.value.content as SearchContent.Results).results.songs.map { it.item }

        viewModel.playSong(1) shouldBe MediaAction.Play(MediaSelection.Songs(results), position = 1)
        preferenceManager.recentSearches shouldBe listOf("juniper")
    }
}
