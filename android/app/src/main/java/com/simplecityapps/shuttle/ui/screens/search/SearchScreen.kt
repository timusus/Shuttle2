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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
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
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2FilterChip
import com.simplecityapps.shuttle.designsystem.component.S2SearchField
import com.simplecityapps.shuttle.designsystem.component.SearchNoResults
import com.simplecityapps.shuttle.designsystem.component.SearchRecentRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SectionHeaderStyle
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab

/** What the user can do on the Search screen; the destination wires each to the ViewModel, navigator or actions host. */
class SearchCallbacks(
    val onSearch: () -> Unit,
    val onSelectAll: () -> Unit,
    val onToggleCategory: (SearchCategory) -> Unit,
    val onRemoveRecentSearch: (String) -> Unit,
    val onSongClick: (index: Int) -> Unit,
    val onAlbumClick: (Album) -> Unit,
    val onArtistClick: (AlbumArtist) -> Unit,
    val onGenreClick: (Genre) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onShowActions: (MediaActionsTarget) -> Unit,
    val onPlay: (MediaSelection) -> Unit,
)

/**
 * The Search destination (redesign inventory, section 2): a search field over filter chips, then the recent searches
 * while the field is empty, or the results grouped by type. [queryState] holds the field's text; the destination
 * feeds it to the ViewModel.
 */
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
        S2SearchField(
            textFieldState = queryState,
            onSearch = {
                callbacks.onSearch()
                focusManager.clearFocus()
            },
            placeholder = stringResource(R.string.search_placeholder),
            focusRequester = focusRequester,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small)
                .fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(S2Spacing.small),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = S2Spacing.medium),
        ) {
            S2FilterChip(
                label = stringResource(R.string.search_category_all),
                selected = uiState.categories.isEmpty(),
                onClick = callbacks.onSelectAll,
            )
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
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(ShellTab.Search, listState)
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "header") { SectionHeader(stringResource(R.string.search_recent), style = SectionHeaderStyle.Title) }
        searches.forEach { query ->
            item(key = "recent:$query") { SearchRecentRow(query, onClick = { onSelect(query) }, onRemove = { onRemove(query) }) }
        }
    }
}

@Composable
private fun SearchResultList(
    query: String,
    results: SearchResults,
    callbacks: SearchCallbacks,
) {
    var expanded by rememberSaveable(query) { mutableStateOf<SearchCategory?>(null) }
    val sections = results.sections(expanded)
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(ShellTab.Search, listState)
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = S2Spacing.medium)) {
        // The best match of all leads, lifted out of its own section.
        results.top?.let { top ->
            item(key = "header:top", contentType = "header") { SectionHeader(stringResource(R.string.search_top_result), style = SectionHeaderStyle.Title) }
            item(key = "top", contentType = top) {
                when (top) {
                    SearchCategory.Artists -> ArtistTopResult(results.artists.first(), callbacks)
                    SearchCategory.Albums -> AlbumTopResult(results.albums.first(), callbacks)
                    SearchCategory.Songs -> SongTopResult(results.songs.first(), 0, callbacks)
                    SearchCategory.Genres -> GenreTopResult(results.genres.first(), callbacks)
                    SearchCategory.Playlists -> PlaylistTopResult(results.playlists.first(), callbacks)
                }
            }
        }
        sections.forEach { section ->
            val type = section.category.name
            item(key = "header:$type", contentType = "header") {
                SectionHeader(
                    title = section.category.label(),
                    style = SectionHeaderStyle.Title,
                    action = if (section.hasMore) stringResource(R.string.search_see_all) else null,
                    onAction = { expanded = section.category },
                )
            }
            // Rows get the hit's index in the whole group, which a song plays from.
            when (section.category) {
                SearchCategory.Artists -> rows(section, results.artists, { it.groupKey }) { _, hit -> ArtistResult(hit, callbacks) }
                SearchCategory.Albums -> rows(section, results.albums, { it.groupKey ?: it.name }) { _, hit -> AlbumResult(hit, callbacks) }
                SearchCategory.Songs -> rows(section, results.songs, { it.id }) { index, hit -> SongResult(hit, index, callbacks) }
                SearchCategory.Genres -> rows(section, results.genres, { it.name }) { _, hit -> GenreResult(hit, callbacks) }
                SearchCategory.Playlists -> rows(section, results.playlists, { it.id }) { _, hit -> PlaylistResult(hit, callbacks) }
            }
        }
    }
}

/** A [section]'s rows from its group's [hits]; each row gets the hit's index in the whole group. */
private fun <T> LazyListScope.rows(
    section: SearchSection,
    hits: List<SearchHit<T>>,
    key: (T) -> Any?,
    row: @Composable (index: Int, hit: SearchHit<T>) -> Unit,
) {
    val type = section.category.name
    items(count = section.until - section.from, key = { "$type:${key(hits[section.from + it].item)}" }, contentType = { type }) { i ->
        row(section.from + i, hits[section.from + i])
    }
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
