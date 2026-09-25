package com.simplecityapps.shuttle.ui.common.mediaactions

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownloadOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionsSheet
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.actions.format
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import kotlinx.coroutines.launch

/** What an actions sheet acts on, and how its header reads. */
data class MediaActionsTarget(
    val title: String,
    val subtitle: String?,
    val selection: MediaSelection,
    val placeholder: ArtworkPlaceholder,
    /** Actions only this screen offers (remove from playlist, rename), listed after the shared ones. */
    val extraActions: List<S2Action> = emptyList(),
)

/**
 * The UI half of a destination's media actions: which sheet, picker or dialog is showing. Screens call
 * [showActions], [perform] and [dispatch]; [MediaActionsHost] renders the rest.
 */
@Stable
class MediaActionsState internal constructor(
    private val onDispatch: (MediaAction) -> Unit,
) {
    var sheet: MediaActionsTarget? by mutableStateOf(null)
        internal set
    var playlistPicker: MediaSelection? by mutableStateOf(null)
        internal set
    internal var createPlaylist: MediaSelection? by mutableStateOf(null)
    internal var confirmation: MediaActionResult.ConfirmationRequired? by mutableStateOf(null)

    fun showActions(target: MediaActionsTarget) {
        sheet = target
    }

    fun dispatch(action: MediaAction) = onDispatch(action)

    /** Runs [type] on [selection]. Add to playlist has no action until a playlist is picked, so it opens the picker. */
    fun perform(type: MediaActionType, selection: MediaSelection) {
        val action = type.actionFor(selection)
        if (action != null) {
            onDispatch(action)
        } else if (type == MediaActionType.AddToPlaylist) {
            playlistPicker = selection
        }
    }
}

/**
 * Hosts one destination's media actions: collects [MediaActionsViewModel.results] into the shell snackbar (with
 * Undo / Add anyway), confirmation dialogs, shares and [onNavigate]; renders the actions sheet and the
 * add-to-playlist picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionsHost(
    onNavigate: (NavigationTarget) -> Unit,
    viewModel: MediaActionsViewModel = hiltViewModel(),
    content: @Composable (MediaActionsState) -> Unit,
) {
    val state = remember(viewModel) { MediaActionsState(viewModel::dispatch) }
    val snackbarHostState = LocalShellSnackbarHostState.current
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    LaunchedEffect(viewModel) {
        viewModel.results.collect { result ->
            when (result) {
                is MediaActionResult.Message -> scope.launch {
                    val snackbarResult = snackbarHostState.showSnackbar(
                        message = result.message.format(resources),
                        actionLabel = result.action?.label?.format(resources),
                        withDismissAction = result.action != null,
                    )
                    if (snackbarResult == SnackbarResult.ActionPerformed) result.action?.let { viewModel.dispatch(it.action) }
                }

                is MediaActionResult.ConfirmationRequired -> state.confirmation = result

                is MediaActionResult.Navigate -> currentOnNavigate(result.target)

                is MediaActionResult.Share -> context.startActivity(Intent.createChooser(result.request.toIntent(), null))

                MediaActionResult.None -> Unit
            }
        }
    }

    content(state)

    state.sheet?.let { target ->
        val types by remember(target.selection) { viewModel.availableActions(target.selection) }.collectAsStateWithLifecycle(emptyList())
        S2ActionsSheet(
            title = target.title,
            subtitle = target.subtitle,
            artwork = { Artwork(target.placeholder, size = ArtworkSize.Small) },
            actions = types.map { type ->
                S2Action(
                    label = type.label(),
                    icon = type.icon,
                    destructive = type == MediaActionType.Delete,
                    onClick = { state.perform(type, target.selection) },
                )
            } + target.extraActions,
            onDismissRequest = { state.sheet = null },
        )
    }

    state.playlistPicker?.let { selection ->
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        PlaylistPickerSheet(
            playlists = playlists,
            onPick = { playlist -> viewModel.dispatch(MediaAction.AddToPlaylist(selection, playlist)) },
            onCreate = { state.createPlaylist = selection },
            onDismissRequest = { state.playlistPicker = null },
        )
    }

    state.createPlaylist?.let { selection ->
        CreatePlaylistDialog(
            onCreate = { name -> viewModel.dispatch(MediaAction.CreatePlaylist(selection, name)) },
            onDismissRequest = { state.createPlaylist = null },
        )
    }

    state.confirmation?.let { confirmation ->
        S2Dialog(
            title = stringResource(R.string.dialog_delete_title),
            confirmLabel = stringResource(R.string.dialog_delete_button),
            dismissLabel = stringResource(R.string.dialog_button_cancel),
            destructive = true,
            onConfirm = {
                viewModel.dispatch(confirmation.confirmAction)
                state.confirmation = null
            },
            onDismissRequest = { state.confirmation = null },
        ) {
            Text(confirmation.message.format(resources))
        }
    }
}

/** The add-to-playlist picker: "New playlist" first, then every playlist. Duplicates come back as a snackbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreate: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    S2ActionsSheet(
        title = stringResource(R.string.menu_title_add_to_playlist),
        actions = listOf(S2Action(stringResource(R.string.playlist_menu_create_playlist), onCreate, Icons.AutoMirrored.Rounded.PlaylistAdd)) +
            playlists.map { playlist -> S2Action(playlist.name, { onPick(playlist) }, Icons.AutoMirrored.Rounded.QueueMusic) },
        onDismissRequest = onDismissRequest,
    )
}

/** Names a new playlist; [onCreate] gets the trimmed name. */
@Composable
fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismissRequest: () -> Unit,
    title: String = stringResource(R.string.playlist_menu_create_playlist),
    initialName: String = "",
    confirmLabel: String = stringResource(R.string.dialog_button_save),
) {
    var name by remember { mutableStateOf(initialName) }
    S2Dialog(
        title = title,
        confirmLabel = confirmLabel,
        dismissLabel = stringResource(R.string.dialog_button_cancel),
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onCreate(name.trim())
            onDismissRequest()
        },
        onDismissRequest = onDismissRequest,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.playlist_create_dialog_playlist_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun MediaActionType.label(): String = stringResource(
    when (this) {
        MediaActionType.Play -> R.string.menu_title_play
        MediaActionType.Shuffle -> R.string.menu_title_shuffle
        MediaActionType.PlayNext -> R.string.menu_title_play_next
        MediaActionType.AddToQueue -> R.string.menu_title_add_to_queue
        MediaActionType.AddToPlaylist -> R.string.menu_title_add_to_playlist
        MediaActionType.GoToAlbum -> R.string.menu_title_view_album
        MediaActionType.GoToArtist -> R.string.media_action_go_to_artist
        MediaActionType.EditTags -> R.string.menu_title_edit_tags
        MediaActionType.SongInfo -> R.string.menu_title_song_info
        MediaActionType.Share -> R.string.media_action_share
        MediaActionType.Exclude -> R.string.menu_title_exclude
        MediaActionType.Delete -> R.string.menu_title_delete
        MediaActionType.Download -> R.string.media_action_download
        MediaActionType.RemoveDownload -> R.string.media_action_remove_download
    },
)

val MediaActionType.icon: ImageVector
    get() = when (this) {
        MediaActionType.Play -> Icons.Rounded.PlayArrow
        MediaActionType.Shuffle -> Icons.Rounded.Shuffle
        MediaActionType.PlayNext -> Icons.Rounded.SkipNext
        MediaActionType.AddToQueue -> Icons.AutoMirrored.Rounded.QueueMusic
        MediaActionType.AddToPlaylist -> Icons.AutoMirrored.Rounded.PlaylistAdd
        MediaActionType.GoToAlbum -> Icons.Rounded.Album
        MediaActionType.GoToArtist -> Icons.Rounded.Person
        MediaActionType.EditTags -> Icons.Rounded.Edit
        MediaActionType.SongInfo -> Icons.Rounded.Info
        MediaActionType.Share -> Icons.Rounded.Share
        MediaActionType.Exclude -> Icons.Rounded.Block
        MediaActionType.Delete -> Icons.Rounded.Delete
        MediaActionType.Download -> Icons.Rounded.Download
        MediaActionType.RemoveDownload -> Icons.Rounded.FileDownloadOff
    }
