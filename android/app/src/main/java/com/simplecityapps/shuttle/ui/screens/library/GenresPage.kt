package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.GenreRow
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListUiState
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab

/** Genres: a list, no multi-select (inventory §1). [covers] are each genre's album covers by name, the placeholder until they load. */
@Composable
fun GenresPage(
    state: GenreListUiState,
    onGenreClick: (Genre) -> Unit,
    onGenreMore: (Genre) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
    covers: Map<String, List<Song>> = emptyMap(),
) {
    val content = when (state.loadingState) {
        GenreListUiState.LoadingState.Loading -> LibraryContentState.Loading
        GenreListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        GenreListUiState.LoadingState.Empty -> LibraryContentState.Empty
        GenreListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.genre_list_empty), modifier, state.scanProgress, controls, hasItems = state.genres.isNotEmpty()) {
        val listState = rememberLazyListState()
        ScrollToTopOnReselect(ShellTab.Library, listState)
        val leadingItems = controlsItemCount(controls)
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-genres")) {
                controlsItem(controls)
                items(state.genres, key = { it.name }) { genre ->
                    GenreRow(
                        name = genre.name,
                        songCount = pluralString(R.plurals.songsPlural, genre.songCount),
                        onClick = { onGenreClick(genre) },
                        onMore = { onGenreMore(genre) },
                        artwork = { CoverMosaic(covers[genre.name].orEmpty(), ArtworkPlaceholder.Genre) },
                    )
                }
            }
            FastScroller(modifier = fastScrollerModifier(controls), getPopupText = { index -> state.genres.getOrNull(index - leadingItems)?.name?.firstOrNull()?.uppercase() }, state = listState)
        }
    }
}
