package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
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
import com.simplecityapps.shuttle.ui.shell.LocalTabReselects
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab
import com.simplecityapps.shuttle.ui.shell.adaptive.LocalShellLayout
import kotlin.math.roundToInt

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
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    queryState: TextFieldState,
    callbacks: SearchCallbacks,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // The keyboard fills a landscape phone, so compact height never raises it unasked.
    val compactHeight = LocalShellLayout.current.compactHeight
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(compactHeight) {
        if (!autoFocused && !compactHeight && queryState.text.isEmpty()) focusRequester.requestFocus()
        autoFocused = true
    }
    val reselects = LocalTabReselects.current
    LaunchedEffect(reselects, compactHeight) {
        reselects.collect { if (it == ShellTab.Search && !compactHeight) focusRequester.requestFocus() }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // A result set too short to scroll can't bring a hidden header back, so new content always starts with it shown.
    val viewState = searchViewState(uiState)
    LaunchedEffect(viewState.content) { scrollBehavior.state.heightOffset = 0f }
    val onEvent: (SearchEvent) -> Unit = { event -> callbacks.onEvent(event) { queryState.edit { replace(0, length, it) } } }
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .statusBarsPadding()
            .then(if (compactHeight) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
    ) {
        Column(if (compactHeight) Modifier.hidesWithScroll(scrollBehavior.state) else Modifier) {
            S2SearchField(
                textFieldState = queryState,
                onSearch = {
                    onEvent(SearchEvent.Search)
                    focusManager.clearFocus()
                },
                placeholder = stringResource(R.string.search_placeholder),
                focusRequester = focusRequester,
                modifier = Modifier
                    .padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small)
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) scrollBehavior.state.heightOffset = 0f },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(S2Spacing.small),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = S2Spacing.medium),
            ) {
                viewState.chips.forEach { chip ->
                    S2FilterChip(
                        label = chip.category?.label() ?: stringResource(R.string.search_category_all),
                        selected = chip.selected,
                        onClick = { onEvent(chip.tapEvent()) },
                    )
                }
            }
        }
        when (val content = viewState.content) {
            SearchViewState.Content.Start -> StartSearching()
            is SearchViewState.Content.Recent -> RecentSearches(content.searches, onEvent)
            SearchViewState.Content.Searching -> LoadingState(Modifier.fillMaxSize())
            is SearchViewState.Content.NoResults -> SearchNoResults(content.query, Modifier.fillMaxSize())
            is SearchViewState.Content.Results -> SearchResultList(content.query, content.results, onEvent)
        }
    }
}

/** Slides the content out of its own space as [state]'s offset grows: an `enterAlways` bar for a header that isn't a TopAppBar. */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.hidesWithScroll(state: TopAppBarState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    state.heightOffsetLimit = -placeable.height.toFloat()
    val offset = state.heightOffset.roundToInt()
    layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) { placeable.place(0, offset) }
}.clipToBounds()

@Composable
private fun StartSearching() {
    EmptyState(
        title = stringResource(R.string.search_start_title),
        message = stringResource(R.string.search_start_message),
        icon = Icons.Rounded.Search,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun RecentSearches(
    searches: List<String>,
    onEvent: (SearchEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(ShellTab.Search, listState)
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "header") { SectionHeader(stringResource(R.string.search_recent), style = SectionHeaderStyle.Title) }
        searches.forEach { query ->
            item(key = "recent:$query") { SearchRecentRow(query, onClick = { onEvent(SearchEvent.SelectRecent(query)) }, onRemove = { onEvent(SearchEvent.RemoveRecent(query)) }) }
        }
    }
}

@Composable
private fun SearchResultList(
    query: String,
    results: SearchResults,
    onEvent: (SearchEvent) -> Unit,
) {
    var expanded by rememberSaveable(query) { mutableStateOf<SearchCategory?>(null) }
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(ShellTab.Search, listState)
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = S2Spacing.medium)) {
        results.resultItems(expanded).forEach { entry ->
            when (entry) {
                is SearchResultItem.TopResult -> {
                    item(key = "header:top", contentType = "header") { SectionHeader(stringResource(R.string.search_top_result), style = SectionHeaderStyle.Title) }
                    item(key = "top", contentType = entry.category) {
                        when (entry.category) {
                            SearchCategory.Artists -> ArtistTopResult(results, onEvent)
                            SearchCategory.Albums -> AlbumTopResult(results, onEvent)
                            SearchCategory.Songs -> SongTopResult(results, onEvent)
                            SearchCategory.Genres -> GenreTopResult(results, onEvent)
                            SearchCategory.Playlists -> PlaylistTopResult(results, onEvent)
                        }
                    }
                }

                is SearchResultItem.Header -> item(key = "header:${entry.category.name}", contentType = "header") {
                    SectionHeader(
                        title = entry.category.label(),
                        style = SectionHeaderStyle.Title,
                        action = if (entry.seeAll) stringResource(R.string.search_see_all) else null,
                        onAction = { expanded = entry.category },
                    )
                }

                is SearchResultItem.Hit -> item(key = "${entry.category.name}:${results.key(entry)}", contentType = entry.category.name) {
                    when (entry.category) {
                        SearchCategory.Artists -> ArtistResult(results, entry.index, onEvent)
                        SearchCategory.Albums -> AlbumResult(results, entry.index, onEvent)
                        SearchCategory.Songs -> SongResult(results, entry.index, onEvent)
                        SearchCategory.Genres -> GenreResult(results, entry.index, onEvent)
                        SearchCategory.Playlists -> PlaylistResult(results, entry.index, onEvent)
                    }
                }
            }
        }
    }
}

private fun SearchResults.key(hit: SearchResultItem.Hit): Any? = when (hit.category) {
    SearchCategory.Artists -> artists[hit.index].item.groupKey
    SearchCategory.Albums -> albums[hit.index].item.let { it.groupKey ?: it.name }
    SearchCategory.Songs -> songs[hit.index].item.id
    SearchCategory.Genres -> genres[hit.index].item.name
    SearchCategory.Playlists -> playlists[hit.index].item.id
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
