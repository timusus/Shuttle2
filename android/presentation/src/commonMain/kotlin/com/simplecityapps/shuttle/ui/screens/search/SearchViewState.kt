package com.simplecityapps.shuttle.ui.screens.search

/** What the Search screen shows for a [SearchUiState]: the filter chips and which content fills the rest. */
data class SearchViewState(
    val chips: List<Chip>,
    val content: Content
) {
    /** A null [category] is the All chip. */
    data class Chip(val category: SearchCategory?, val selected: Boolean)

    sealed interface Content {
        /** No recent searches either: invites a first search. */
        data object Start : Content

        data class Recent(val searches: List<String>) : Content

        data object Searching : Content

        data class NoResults(val query: String) : Content

        data class Results(val query: String, val results: SearchResults) : Content
    }
}

fun searchViewState(uiState: SearchUiState): SearchViewState = SearchViewState(
    chips = listOf(SearchViewState.Chip(category = null, selected = uiState.categories.isEmpty())) +
        SearchCategory.entries.map { SearchViewState.Chip(it, selected = it in uiState.categories) },
    content = when (val content = uiState.content) {
        is SearchContent.Recent -> if (content.searches.isEmpty()) SearchViewState.Content.Start else SearchViewState.Content.Recent(content.searches)
        SearchContent.Searching -> SearchViewState.Content.Searching
        is SearchContent.NoResults -> SearchViewState.Content.NoResults(content.query)
        is SearchContent.Results -> SearchViewState.Content.Results(content.query, content.results)
    }
)
