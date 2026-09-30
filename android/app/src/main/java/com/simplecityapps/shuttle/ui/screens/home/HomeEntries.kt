package com.simplecityapps.shuttle.ui.screens.home

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.screens.library.GenreRoute
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyScreen
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel
import com.simplecityapps.shuttle.ui.screens.library.PlaylistRoute
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistRoute
import com.simplecityapps.shuttle.ui.screens.library.openTarget
import com.simplecityapps.shuttle.ui.screens.library.rememberMusicAccessRequests
import com.simplecityapps.shuttle.ui.screens.library.route
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationRoute
import com.simplecityapps.shuttle.ui.screens.settings.WhatsNewRoute
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerRoute
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.HomeRoute
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import com.simplecityapps.shuttle.ui.shell.SettingsRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel

fun EntryProviderScope<NavKey>.homeEntries(navigator: AppNavigator) {
    entry<HomeRoute> {
        HomeDestination(onOpen = navigator::open, onNavigate = navigator::openTarget)
    }
}

@Composable
private fun HomeDestination(
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
    viewModel: HomeViewModel = metroViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Home reloads as it comes on screen, and while it's off screen only as the hour turns (#672)
    HomeVisibilityEffect(viewModel::onVisibilityChanged)
    val emptyViewModel: LibraryEmptyViewModel = metroViewModel()
    val emptyState by emptyViewModel.uiState.collectAsStateWithLifecycle()
    val accessRequests = rememberMusicAccessRequests(emptyViewModel)
    val snackbarHostState = LocalShellSnackbarHostState.current
    val analyticsNoticeMessage = stringResource(R.string.home_analytics_notice_message)
    val analyticsNoticeAction = stringResource(R.string.home_analytics_notice_action)
    var connectingServer by rememberSaveable { mutableStateOf(false) }
    if (connectingServer) {
        ServerTypePickerRoute(onDismissRequest = { connectingServer = false })
    }
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
                onOpenItem = { item ->
                    when (item) {
                        is HomeItem.AlbumItem -> onOpen(item.album.route)
                        is HomeItem.ArtistItem -> onOpen(item.albumArtist.route)
                        is HomeItem.PlaylistItem -> onOpen(PlaylistRoute(item.playlist.id))
                        is HomeItem.SmartPlaylistItem -> onOpen(SmartPlaylistRoute(item.smartPlaylistId.id))
                        is HomeItem.GenreItem -> onOpen(GenreRoute(item.genre.name))
                    }
                },
                onRefresh = viewModel::refresh,
                onAction = actions::dispatch,
                onShowActions = actions::showActions,
                onSeeAll = { section ->
                    // Only Recently added has a See all (HomeSectionId.hasSeeAll): the smart playlist of the same name.
                    if (section == HomeSectionId.RecentlyAdded) onOpen(SmartPlaylistRoute(SmartPlaylistId.RecentlyAdded.id))
                },
            ),
            emptyContent = (emptyState as? LibraryAvailability.Empty)?.let { empty ->
                @Composable { modifier: Modifier ->
                    LibraryEmptyScreen(
                        state = empty,
                        onAllowAccess = accessRequests.request,
                        onOpenAppSettings = accessRequests.openAppSettings,
                        onScan = emptyViewModel::onScan,
                        onConnectServer = { connectingServer = true },
                        modifier = modifier,
                    )
                }
            },
        )
    }
}

/**
 * Tells [onVisibilityChanged] whether Home is on screen: shown while its entry is composed and the app is started. The
 * shell's NavDisplay composes only the scene on show, so another tab, or a screen pushed over Home, disposes the entry
 * and this reports it hidden; coming back composes it again (AppShellTest).
 */
@Composable
internal fun HomeVisibilityEffect(onVisibilityChanged: (Boolean) -> Unit) {
    val onChanged by rememberUpdatedState(onVisibilityChanged)
    LifecycleStartEffect(Unit) {
        onChanged(true)
        onStopOrDispose { onChanged(false) }
    }
}
