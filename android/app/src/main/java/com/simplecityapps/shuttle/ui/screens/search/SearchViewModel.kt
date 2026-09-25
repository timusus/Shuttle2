package com.simplecityapps.shuttle.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the area below the search field shows. */
sealed interface SearchContent {
    /** No query yet: the recent searches, newest first. */
    data class Recent(val searches: List<String>) : SearchContent

    /** The query changed and its results aren't in yet. */
    data object Searching : SearchContent

    data class Results(val query: String, val results: SearchResults) : SearchContent

    data class NoResults(val query: String) : SearchContent
}

data class SearchUiState(
    val categories: Set<SearchCategory> = SearchCategory.entries.toSet(),
    val content: SearchContent = SearchContent.Recent(emptyList()),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchLibrary: SearchLibrary,
    private val recentSearches: RecentSearches,
    private val preferenceManager: GeneralPreferenceManager,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val categories = MutableStateFlow(preferenceManager.searchCategories())

    private val content = combine(query, categories) { query, categories -> query.trim() to categories }
        // Typing waits out the debounce; clearing the field shows the recent searches at once.
        .debounce { (query, _) -> if (query.isEmpty()) 0.milliseconds else SearchDebounce }
        .flatMapLatest { (query, categories) ->
            if (query.isEmpty()) {
                recentSearches.searches.map { SearchContent.Recent(it) }
            } else {
                searchLibrary(query, categories)
                    .map { results -> if (results.isEmpty) SearchContent.NoResults(query) else SearchContent.Results(query, results) }
                    .onStart<SearchContent> { emit(SearchContent.Searching) }
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(categories, content) { categories, content -> SearchUiState(categories, content) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(categories.value, SearchContent.Recent(recentSearches.searches.value)))

    fun onQueryChange(query: String) {
        this.query.value = query
    }

    fun onToggleCategory(category: SearchCategory) {
        categories.update { if (category in it) it - category else it + category }
        preferenceManager.saveSearchCategories(categories.value)
    }

    /** The query was submitted from the keyboard. */
    fun onSearch() {
        recentSearches.add(query.value)
    }

    /** A result was opened or played, so the query that found it is worth keeping. */
    fun onResultChosen() {
        recentSearches.add(query.value)
    }

    fun onRemoveRecentSearch(query: String) {
        recentSearches.remove(query)
    }

    /** Tapping a song plays every song result, starting at the tapped one. */
    fun playSong(index: Int): MediaAction? {
        val results = (uiState.value.content as? SearchContent.Results)?.results ?: return null
        onResultChosen()
        return MediaAction.Play(MediaSelection.Songs(results.songs), position = index)
    }

    companion object {
        val SearchDebounce = 500.milliseconds
    }
}

private fun GeneralPreferenceManager.searchCategories(): Set<SearchCategory> = buildSet {
    if (searchFilterArtists) add(SearchCategory.Artists)
    if (searchFilterAlbums) add(SearchCategory.Albums)
    if (searchFilterSongs) add(SearchCategory.Songs)
    if (searchFilterGenres) add(SearchCategory.Genres)
    if (searchFilterPlaylists) add(SearchCategory.Playlists)
}

private fun GeneralPreferenceManager.saveSearchCategories(categories: Set<SearchCategory>) {
    searchFilterArtists = SearchCategory.Artists in categories
    searchFilterAlbums = SearchCategory.Albums in categories
    searchFilterSongs = SearchCategory.Songs in categories
    searchFilterGenres = SearchCategory.Genres in categories
    searchFilterPlaylists = SearchCategory.Playlists in categories
}
