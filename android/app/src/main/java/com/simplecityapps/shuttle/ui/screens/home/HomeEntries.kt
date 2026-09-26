package com.simplecityapps.shuttle.ui.screens.home

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyScreen
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel
import com.simplecityapps.shuttle.ui.screens.library.openTarget
import com.simplecityapps.shuttle.ui.screens.library.rememberMusicAccessRequests
import com.simplecityapps.shuttle.ui.screens.library.route
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationRoute
import com.simplecityapps.shuttle.ui.screens.settings.WhatsNewRoute
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.HomeRoute
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import com.simplecityapps.shuttle.ui.shell.SettingsRoute

fun EntryProviderScope<NavKey>.homeEntries(navigator: AppNavigator) {
    entry<HomeRoute> {
        HomeDestination(onOpen = navigator::open, onNavigate = navigator::openTarget)
    }
}

@Composable
private fun HomeDestination(
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emptyViewModel: LibraryEmptyViewModel = hiltViewModel()
    val emptyState by emptyViewModel.uiState.collectAsStateWithLifecycle()
    val accessRequests = rememberMusicAccessRequests(emptyViewModel)
    val snackbarHostState = LocalShellSnackbarHostState.current
    val analyticsNoticeMessage = stringResource(R.string.home_analytics_notice_message)
    val analyticsNoticeAction = stringResource(R.string.home_analytics_notice_action)
    (uiState as? HomeUiState.Content)?.let { content ->
        ConsumeEvents(content.events, onConsumed = viewModel::onEventHandled) { event ->
            when (event) {
                HomeEvent.AnalyticsNowOn -> {
                    val result = snackbarHostState.showSnackbar(analyticsNoticeMessage, actionLabel = analyticsNoticeAction, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) onOpen(SettingsDestinationRoute(SettingsDestination.Privacy))
                }
            }
        }
    }
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        HomeScreen(
            uiState = uiState,
            callbacks = HomeCallbacks(
                onOpenSettings = { onOpen(SettingsRoute) },
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
            emptyContent = (emptyState as? LibraryAvailability.Empty)?.let { empty ->
                @Composable { modifier: Modifier ->
                    LibraryEmptyScreen(
                        state = empty,
                        onAllowAccess = accessRequests.request,
                        onOpenAppSettings = accessRequests.openAppSettings,
                        onScan = emptyViewModel::onScan,
                        onConnectServer = { onOpen(SettingsDestinationRoute(SettingsDestination.Sources)) },
                        modifier = modifier,
                    )
                }
            },
        )
    }
}
