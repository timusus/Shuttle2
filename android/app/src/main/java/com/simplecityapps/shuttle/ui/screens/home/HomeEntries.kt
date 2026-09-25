package com.simplecityapps.shuttle.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.screens.library.openTarget
import com.simplecityapps.shuttle.ui.screens.library.route
import com.simplecityapps.shuttle.ui.screens.settings.WhatsNewRoute
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.HomeRoute
import com.simplecityapps.shuttle.ui.shell.ShellTab

fun EntryProviderScope<NavKey>.homeEntries(navigator: AppNavigator) {
    entry<HomeRoute> {
        HomeDestination(onOpen = navigator::open, onNavigate = navigator::openTarget, onSearch = { navigator.selectTab(ShellTab.Search) })
    }
}

@Composable
private fun HomeDestination(
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        HomeScreen(
            uiState = uiState,
            callbacks = HomeCallbacks(
                onSearch = onSearch,
                onShuffleAll = { viewModel.shuffleAll()?.let(actions::dispatch) },
                onOpenWhatsNew = {
                    viewModel.onWhatsNewHandled()
                    onOpen(WhatsNewRoute)
                },
                onDismissWhatsNew = viewModel::onWhatsNewHandled,
                onAlbumClick = { onOpen(it.route) },
                onArtistClick = { onOpen(it.route) },
                onShowActions = actions::showActions,
            ),
        )
    }
}
