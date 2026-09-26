package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.screens.library.GenreRoute
import com.simplecityapps.shuttle.ui.screens.library.PlaylistRoute
import com.simplecityapps.shuttle.ui.screens.library.openTarget
import com.simplecityapps.shuttle.ui.screens.library.route
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.SearchRoute

fun EntryProviderScope<NavKey>.searchEntries(navigator: AppNavigator) {
    entry<SearchRoute> { SearchDestination(onOpen = navigator::open, onNavigate = navigator::openTarget) }
}

@Composable
private fun SearchDestination(
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queryState = rememberTextFieldState()
    LaunchedEffect(queryState, viewModel) { snapshotFlow { queryState.text.toString() }.collect(viewModel::onQueryChange) }

    MediaActionsHost(onNavigate = onNavigate) { actions ->
        val open = { route: NavKey ->
            viewModel.onResultChosen()
            onOpen(route)
        }
        SearchScreen(
            uiState = uiState,
            queryState = queryState,
            callbacks = SearchCallbacks(
                onSearch = viewModel::onSearch,
                onSelectAll = viewModel::onSelectAll,
                onToggleCategory = viewModel::onToggleCategory,
                onRemoveRecentSearch = viewModel::onRemoveRecentSearch,
                onSongClick = { index -> viewModel.playSong(index)?.let(actions::dispatch) },
                onAlbumClick = { open(it.route) },
                onArtistClick = { open(it.route) },
                onGenreClick = { open(GenreRoute(it.name)) },
                onPlaylistClick = { open(PlaylistRoute(it.id)) },
                onShowActions = actions::showActions,
            ),
        )
    }
}
