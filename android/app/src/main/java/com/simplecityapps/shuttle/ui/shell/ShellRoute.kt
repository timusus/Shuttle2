package com.simplecityapps.shuttle.ui.shell

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.downloads.DownloadStatusViewModel
import com.simplecityapps.shuttle.ui.common.downloads.ProvideDownloadStatuses
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInRoute
import com.simplecityapps.shuttle.ui.screens.sources.titleRes
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiEvent
import com.simplecityapps.shuttle.ui.shell.player.PlayerViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The shell wired to its ViewModels: the start tab from [shellViewModel], and the player's state, progress and
 * actions, with its events as snackbars.
 */
@Composable
fun ShellRoute(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = metroViewModel(),
    shellViewModel: ShellViewModel = metroViewModel(),
    downloadStatus: DownloadStatusViewModel = metroViewModel(),
    tabRequests: Flow<ShellTab> = emptyFlow(),
) {
    val playerState = viewModel.uiState.collectAsStateWithLifecycle()
    // Read apart from the progress, so a tick recomposes only what reads the progress.
    val playerUi by remember { derivedStateOf { playerState.value.player } }
    val shellUi by shellViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var signingIn by rememberSaveable { mutableStateOf<MediaProviderType?>(null) }
    PlayerEventsEffect(playerState.value.events, viewModel::onEventHandled, snackbarHostState, actions = viewModel)
    ShellEventsEffect(shellUi.events, shellViewModel::onEventHandled, snackbarHostState, onSignIn = { signingIn = it })
    signingIn?.let { type ->
        ServerSignInRoute(type, onConnected = shellViewModel::onServerConnected, onDismiss = { signingIn = null })
    }
    ProvideDownloadStatuses(downloadStatus.uiState.collectAsStateWithLifecycle()) {
        AppShell(
            playerUi = playerUi,
            progress = { playerState.value.progress },
            actions = viewModel,
            modifier = modifier,
            snackbarHostState = snackbarHostState,
            startTab = shellUi.startTab,
            tabRequests = tabRequests,
        )
    }
}

/** Tells the user a server signed them out, with Sign in to open that server's dialog (#595). */
@Composable
internal fun ShellEventsEffect(
    events: List<PendingEvent<ShellEvent>>,
    onEventHandled: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    onSignIn: (MediaProviderType) -> Unit,
) {
    val resources = LocalContext.current.resources
    ConsumeEvents(events, onEventHandled) { event ->
        when (event) {
            is ShellEvent.ServerSignedOut -> {
                val result = snackbarHostState.showSnackbar(
                    resources.getString(R.string.shell_server_signed_out, resources.getString(event.type.titleRes)),
                    actionLabel = resources.getString(R.string.shell_server_sign_in),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) onSignIn(event.type)
            }
        }
    }
}

/**
 * Carries out the player's events, one at a time, as the state holds them pending: a cleared queue or
 * removed row offers Undo, and a skipped server song says why.
 */
@Composable
internal fun PlayerEventsEffect(
    events: List<PendingEvent<PlayerUiEvent>>,
    onEventHandled: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    actions: PlayerActions,
) {
    val queueCleared = stringResource(R.string.player_queue_cleared)
    val removedFromQueue = stringResource(R.string.player_removed_from_queue)
    val undo = stringResource(R.string.player_undo)
    val resources = LocalContext.current.resources
    ConsumeEvents(events, onEventHandled) { event ->
        when (event) {
            is PlayerUiEvent.QueueCleared -> {
                val result = snackbarHostState.showSnackbar(queueCleared, actionLabel = undo, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) actions.undoClearQueue()
            }

            is PlayerUiEvent.QueueItemRemoved -> {
                val result = snackbarHostState.showSnackbar(removedFromQueue, actionLabel = undo, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) actions.undoRemoveQueueItem()
            }

            // The destinations' MediaActionsHost shows Android's song action results; only iOS posts this.
            is PlayerUiEvent.MediaActionDone -> Unit

            is PlayerUiEvent.ServerSongSkipped -> snackbarHostState.showSnackbar(
                resources.getString(R.string.player_server_song_skipped, event.songTitle),
                duration = SnackbarDuration.Short,
            )
        }
    }
}
