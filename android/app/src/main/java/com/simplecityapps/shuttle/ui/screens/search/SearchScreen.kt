package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2FilterChip
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.SearchNoResults
import com.simplecityapps.shuttle.designsystem.component.SearchRecentRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget

/** What the user can do on the Search screen; the destination wires each to the ViewModel, navigator or actions host. */
class SearchCallbacks(
    val onSearch: () -> Unit,
    val onToggleCategory: (SearchCategory) -> Unit,
    val onRemoveRecentSearch: (String) -> Unit,
    val onSongClick: (index: Int) -> Unit,
    val onAlbumClick: (Album) -> Unit,
    val onArtistClick: (AlbumArtist) -> Unit,
    val onGenreClick: (Genre) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onShowActions: (MediaActionsTarget) -> Unit,
)

/**
 * The Search destination (redesign inventory, section 2): a search field over filter chips, then the recent searches
 * while the field is empty, or the results grouped by type. [queryState] holds the field's text; the destination
 * feeds it to the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    queryState: TextFieldState,
    callbacks: SearchCallbacks,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Arriving with nothing typed means the user wants to type.
    LaunchedEffect(Unit) { if (queryState.text.isEmpty()) focusRequester.requestFocus() }

    Column(modifier.fillMaxSize()) {
        Surface(
            shape = SearchBarDefaults.inputFieldShape,
            color = SearchBarDefaults.colors().containerColor,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            SearchBarDefaults.InputField(
                state = queryState,
                onSearch = {
                    callbacks.onSearch()
                    focusManager.clearFocus()
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (queryState.text.isNotEmpty()) {
                    { S2IconButton(Icons.Rounded.Close, stringResource(com.simplecityapps.shuttle.designsystem.R.string.ds_clear_search), { queryState.clearText() }) }
                } else {
                    null
                },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SearchCategory.entries.forEach { category ->
                S2FilterChip(
                    label = category.label(),
                    selected = category in uiState.categories,
                    onClick = { callbacks.onToggleCategory(category) },
                )
            }
        }
        when (val content = uiState.content) {
            is SearchContent.Recent -> RecentSearches(content.searches, onSelect = { queryState.edit { replace(0, length, it) } }, onRemove = callbacks.onRemoveRecentSearch)
            SearchContent.Searching -> LoadingState(Modifier.fillMaxSize())
            is SearchContent.NoResults -> SearchNoResults(content.query, Modifier.fillMaxSize())
            is SearchContent.Results -> SearchResultList(content.query, content.results, callbacks)
        }
    }
}

@Composable
private fun RecentSearches(
    searches: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (searches.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.search_start_title),
            message = stringResource(R.string.search_start_message),
            icon = Icons.Rounded.Search,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") { SectionHeader(stringResource(R.string.search_recent)) }
        searches.forEach { query ->
            item(key = "recent:$query") { SearchRecentRow(query, onClick = { onSelect(query) }, onRemove = { onRemove(query) }) }
        }
    }
}

/** How many hits each section shows before "See all", unless it's the only section with results. */
private val SectionLimits = mapOf(
    SearchCategory.Artists to 3,
    SearchCategory.Albums to 3,
    SearchCategory.Songs to 5,
    SearchCategory.Genres to 3,
    SearchCategory.Playlists to 3,
)

@Composable
private fun SearchResultList(
    query: String,
    results: SearchResults,
    callbacks: SearchCallbacks,
) {
    var expanded by rememberSaveable(query) { mutableStateOf<SearchCategory?>(null) }
    val onlySection = listOf(results.artists, results.albums, results.songs, results.genres, results.playlists).count { it.isNotEmpty() } == 1
    val limit = { category: SearchCategory -> if (onlySection || category == expanded) Int.MAX_VALUE else SectionLimits.getValue(category) }
    val showAll = { category: SearchCategory -> expanded = category }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        // The best match of all leads, lifted out of its own section.
        results.top?.let { top ->
            item(key = "header:top", contentType = "header") { SectionHeader(stringResource(R.string.search_top_result)) }
            item(key = "top", contentType = top) {
                when (top) {
                    SearchCategory.Artists -> ArtistResult(results.artists.first(), callbacks)
                    SearchCategory.Albums -> AlbumResult(results.albums.first(), callbacks)
                    SearchCategory.Songs -> SongResult(results.songs.first(), 0, callbacks)
                    SearchCategory.Genres -> GenreResult(results.genres.first(), callbacks)
                    SearchCategory.Playlists -> PlaylistResult(results.playlists.first(), callbacks)
                }
            }
        }
        section(SearchCategory.Artists, results.artists, results.top, limit, showAll, { it.groupKey }) { _, hit -> ArtistResult(hit, callbacks) }
        section(SearchCategory.Albums, results.albums, results.top, limit, showAll, { it.groupKey ?: it.name }) { _, hit -> AlbumResult(hit, callbacks) }
        section(SearchCategory.Songs, results.songs, results.top, limit, showAll, { it.id }) { index, hit -> SongResult(hit, index, callbacks) }
        section(SearchCategory.Genres, results.genres, results.top, limit, showAll, { it.name }) { _, hit -> GenreResult(hit, callbacks) }
        section(SearchCategory.Playlists, results.playlists, results.top, limit, showAll, { it.id }) { _, hit -> PlaylistResult(hit, callbacks) }
    }
}

/**
 * One result group: a header, then a row per hit, up to its [limit] with "See all" in the header past it. The group's
 * first hit is left out when it's the [top] result. Rows get the hit's index in the whole group.
 */
private fun <T> LazyListScope.section(
    category: SearchCategory,
    hits: List<SearchHit<T>>,
    top: SearchCategory?,
    limit: (SearchCategory) -> Int,
    onShowAll: (SearchCategory) -> Unit,
    key: (T) -> Any?,
    row: @Composable (index: Int, hit: SearchHit<T>) -> Unit,
) {
    val from = if (top == category) 1 else 0
    if (hits.size <= from) return
    val until = (from + limit(category).toLong()).coerceAtMost(hits.size.toLong()).toInt()
    val type = category.name
    item(key = "header:$type", contentType = "header") {
        SectionHeader(
            title = category.label(),
            action = if (until < hits.size) stringResource(R.string.search_see_all) else null,
            onAction = { onShowAll(category) },
        )
    }
    items(count = until - from, key = { "$type:${key(hits[from + it].item)}" }, contentType = { type }) { i -> row(from + i, hits[from + i]) }
}

@Composable
internal fun SearchCategory.label(): String = stringResource(
    when (this) {
        SearchCategory.Artists -> R.string.search_category_artists
        SearchCategory.Albums -> R.string.search_category_albums
        SearchCategory.Songs -> R.string.search_category_songs
        SearchCategory.Genres -> R.string.search_category_genres
        SearchCategory.Playlists -> R.string.search_category_playlists
    },
)
