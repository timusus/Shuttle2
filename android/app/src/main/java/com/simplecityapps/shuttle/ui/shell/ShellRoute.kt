package com.simplecityapps.shuttle.ui.shell

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiEvent
import com.simplecityapps.shuttle.ui.shell.player.PlayerViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The shell wired to the player's ViewModel: its state, progress and actions, and its events as snackbars. */
@Composable
fun ShellRoute(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playerUi by viewModel.uiState.collectAsStateWithLifecycle()
    val progress = viewModel.progress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    PlayerEventsEffect(viewModel.events, snackbarHostState, onUndoClearQueue = viewModel::undoClearQueue)
    AppShell(
        playerUi = playerUi,
        progress = { progress.value },
        actions = viewModel,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Shows the player's events as snackbars while the screen is started: a cleared queue offers Undo. */
@Composable
internal fun PlayerEventsEffect(
    events: Flow<PlayerUiEvent>,
    snackbarHostState: SnackbarHostState,
    onUndoClearQueue: () -> Unit,
) {
    val queueCleared = stringResource(R.string.player_queue_cleared)
    val undo = stringResource(R.string.player_undo)
    val onUndo by rememberUpdatedState(onUndoClearQueue)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(events, snackbarHostState, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { event ->
                when (event) {
                    is PlayerUiEvent.QueueCleared -> launch {
                        val result = snackbarHostState.showSnackbar(queueCleared, actionLabel = undo, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) onUndo()
                    }
                }
            }
        }
    }
}
