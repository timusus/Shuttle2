package com.simplecityapps.shuttle.ui.shell

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.actions.format
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiEvent
import com.simplecityapps.shuttle.ui.shell.player.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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
    val targets = remember { Channel<NavigationTarget>(Channel.UNLIMITED) }
    PlayerEventsEffect(viewModel.events, snackbarHostState, actions = viewModel, onNavigate = { targets.trySend(it) })
    AppShell(
        playerUi = playerUi,
        progress = { progress.value },
        actions = viewModel,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        navigationRequests = remember(targets) { targets.receiveAsFlow() },
    )
}

/**
 * Carries out the player's events while the screen is started: a cleared queue or removed row offers
 * Undo, and a song action's result shows its message, opens its album or artist screen through
 * [onNavigate] or opens the legacy tag editor and song info dialogs.
 */
@Composable
internal fun PlayerEventsEffect(
    events: Flow<PlayerUiEvent>,
    snackbarHostState: SnackbarHostState,
    actions: PlayerActions,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val queueCleared = stringResource(R.string.player_queue_cleared)
    val removedFromQueue = stringResource(R.string.player_removed_from_queue)
    val undo = stringResource(R.string.player_undo)
    val resources = LocalContext.current.resources
    val activity = LocalActivity.current
    val currentActions by rememberUpdatedState(actions)
    val navigate by rememberUpdatedState(onNavigate)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(events, snackbarHostState, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            events.collect { event ->
                when (event) {
                    is PlayerUiEvent.QueueCleared -> launch {
                        val result = snackbarHostState.showSnackbar(queueCleared, actionLabel = undo, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) currentActions.undoClearQueue()
                    }

                    is PlayerUiEvent.QueueItemRemoved -> launch {
                        val result = snackbarHostState.showSnackbar(removedFromQueue, actionLabel = undo, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) currentActions.undoRemoveQueueItem()
                    }

                    is PlayerUiEvent.MediaActionDone -> onMediaActionResult(event.result, snackbarHostState, resources, activity, currentActions, navigate)
                }
            }
        }
    }
}

private fun CoroutineScope.onMediaActionResult(
    result: MediaActionResult,
    snackbarHostState: SnackbarHostState,
    resources: Resources,
    activity: Activity?,
    actions: PlayerActions,
    onNavigate: (NavigationTarget) -> Unit,
) {
    when (result) {
        is MediaActionResult.None -> Unit

        is MediaActionResult.Message -> launch {
            val action = result.action
            val shown = snackbarHostState.showSnackbar(
                result.message.format(resources),
                actionLabel = action?.label?.format(resources),
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (shown == SnackbarResult.ActionPerformed && action != null) actions.onMediaAction(action.action)
        }

        is MediaActionResult.Navigate -> when (val target = result.target) {
            is NavigationTarget.Album, is NavigationTarget.AlbumArtist -> onNavigate(target)

            // The tag editor and song info are still fragments; they open over the shell until they move to Compose.
            is NavigationTarget.TagEditor -> (activity as? FragmentActivity)?.let { TagEditorAlertDialog.newInstance(target.songs).show(it.supportFragmentManager) }

            is NavigationTarget.SongInfo -> (activity as? FragmentActivity)?.let { SongInfoDialogFragment.newInstance(target.song).show(it.supportFragmentManager) }
        }

        is MediaActionResult.Share -> activity?.startActivity(Intent.createChooser(result.request.toIntent(), null))

        // The player offers no action that asks first (Delete isn't in its menus).
        is MediaActionResult.ConfirmationRequired -> Unit
    }
}
