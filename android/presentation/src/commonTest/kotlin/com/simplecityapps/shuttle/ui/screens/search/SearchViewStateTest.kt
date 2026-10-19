package com.simplecityapps.shuttle.ui.screens.search

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SearchViewStateTest {
    private fun selected(state: SearchViewState) = state.chips.filter { it.selected }.map { it.category }

    @Test
    fun `chips lead with All then every category in order`() {
        val state = searchViewState(SearchUiState())

        state.chips.map { it.category } shouldBe listOf(null) + SearchCategory.entries
    }

    @Test
    fun `with no categories chosen only All is selected`() {
        selected(searchViewState(SearchUiState())) shouldBe listOf(null)
    }

    @Test
    fun `chosen categories are selected and All is not`() {
        val state = searchViewState(SearchUiState(categories = setOf(SearchCategory.Songs, SearchCategory.Albums)))

        selected(state) shouldBe listOf(SearchCategory.Albums, SearchCategory.Songs)
    }

    @Test
    fun `no recent searches invites a first search`() {
        searchViewState(SearchUiState(content = SearchContent.Recent(emptyList()))).content shouldBe SearchViewState.Content.Start
    }

    @Test
    fun `recent searches list in the order given`() {
        val state = searchViewState(SearchUiState(content = SearchContent.Recent(listOf("nightjar", "kestrel"))))

        state.content shouldBe SearchViewState.Content.Recent(listOf("nightjar", "kestrel"))
    }

    @Test
    fun `a first search in flight shows searching`() {
        searchViewState(SearchUiState(content = SearchContent.Searching)).content shouldBe SearchViewState.Content.Searching
    }

    @Test
    fun `a query that finds nothing carries the query`() {
        searchViewState(SearchUiState(content = SearchContent.NoResults("zzzz"))).content shouldBe SearchViewState.Content.NoResults("zzzz")
    }

    @Test
    fun `results carry the query and the grouped hits`() {
        val results = SearchResults(top = SearchCategory.Songs)

        searchViewState(SearchUiState(content = SearchContent.Results("night", results))).content shouldBe SearchViewState.Content.Results("night", results)
    }
}
