package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellWidth
import dev.zacsweers.metrox.viewmodel.metroViewModel

fun EntryProviderScope<NavKey>.searchEntries(navigator: AppNavigator) {
    entry<SearchRoute> { SearchDestination(onOpen = navigator::open, onNavigate = navigator::openTarget) }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun SearchDestination(
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
    viewModel: SearchViewModel = metroViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queryState = rememberTextFieldState()
    LaunchedEffect(queryState, viewModel) { snapshotFlow { queryState.text.toString() }.collect(viewModel::onQueryChange) }
    // The search view is full screen below Expanded, docked under the bar from Expanded.
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val docked = remember(windowAdaptiveInfo) { ShellLayout.from(windowAdaptiveInfo).width >= ShellWidth.Expanded }

    MediaActionsHost(onNavigate = onNavigate) { actions ->
        val open = { route: NavKey ->
            viewModel.onResultChosen()
            onOpen(route)
        }
        SearchScreen(
            uiState = uiState,
            queryState = queryState,
            docked = docked,
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
