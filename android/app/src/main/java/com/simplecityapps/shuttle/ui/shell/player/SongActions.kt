package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionsSheet
import com.simplecityapps.shuttle.designsystem.component.S2DialogContent
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection

/**
 * Which of the player's song sheets is open: a song's actions, then the playlist picker and the new
 * playlist dialog that Add to playlist (or Save queue to playlist) leads to. One at a time, so each
 * replaces the last.
 */
@Stable
internal class SongActionsState {
    var menuFor by mutableStateOf<PlayerSong?>(null)
    var playlistFor by mutableStateOf<PlaylistPick?>(null)
    var newPlaylistFor by mutableStateOf<MediaSelection?>(null)
}

/** What the playlist picker adds: [selection], named under the picker's title by [subtitle]. */
internal data class PlaylistPick(val selection: MediaSelection, val subtitle: String?)

@Composable
internal fun rememberSongActionsState(): SongActionsState = remember { SongActionsState() }

/**
 * The open song sheet of [state]: the queue's own [leading] actions (Play next, Remove), the shared
 * actions [PlayerActions.songActions] allows, sent through [PlayerActions.onMediaAction], then [trailing] (Save queue to playlist, Clear queue).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongActionsHost(
    state: SongActionsState,
    actions: PlayerActions,
    leading: @Composable (PlayerSong) -> List<S2Action> = { emptyList() },
    trailing: List<S2Action> = emptyList(),
) {
    state.menuFor?.let { row ->
        val available by remember(row.song) { actions.songActions(row.song) }.collectAsState(initial = emptyList())
        val selection = MediaSelection.Songs(row.song)
        val shared = available.mapNotNull { type ->
            val label = type.labelRes?.let { stringResource(it) } ?: return@mapNotNull null
            val onClick = if (type == MediaActionType.AddToPlaylist) {
                { state.playlistFor = PlaylistPick(selection, row.song.name) }
            } else {
                { type.actionFor(selection)?.let(actions::onMediaAction) ?: Unit }
            }
            S2Action(label = label, onClick = onClick, icon = type.icon)
        }
        S2ActionsSheet(
            title = row.title,
            subtitle = row.artist,
            artwork = { SongArtwork(row.song) },
            actions = leading(row) + shared + trailing,
            onDismissRequest = { state.menuFor = null },
        )
    }
    state.playlistFor?.let { (selection, subtitle) ->
        val playlists by remember { actions.playlists() }.collectAsState(initial = emptyList())
        S2ActionsSheet(
            title = stringResource(R.string.menu_title_add_to_playlist),
            subtitle = subtitle,
            actions = listOf(S2Action(stringResource(R.string.playlist_menu_create_playlist), { state.newPlaylistFor = selection }, Icons.Rounded.Add)) +
                playlists.map { playlist ->
                    S2Action(playlist.name, { actions.onMediaAction(MediaAction.AddToPlaylist(selection, playlist)) }, Icons.AutoMirrored.Rounded.QueueMusic)
                },
            onDismissRequest = { state.playlistFor = null },
        )
    }
    state.newPlaylistFor?.let { selection ->
        NewPlaylistDialog(
            onCreate = { name -> actions.onMediaAction(MediaAction.CreatePlaylist(selection, name)) },
            onDismiss = { state.newPlaylistFor = null },
        )
    }
}

/** Names a new playlist; any non-blank name will do, duplicates included, as before. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewPlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        NewPlaylistForm(onCreate, onDismiss)
    }
}

/**
 * The [NewPlaylistDialog]'s surface, without its window. Under Robolectric a text field in a dialog
 * window never lets Compose idle, so tests render this.
 */
@Composable
internal fun NewPlaylistForm(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    S2DialogContent(
        title = stringResource(R.string.playlist_menu_create_playlist),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.player_create_playlist),
        onConfirm = {
            onCreate(name.trim())
            onDismiss()
        },
        dismissLabel = stringResource(R.string.dialog_button_cancel),
        confirmEnabled = name.isNotBlank(),
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

/** The menu label of the shared actions the player offers; null for the rest. */
private val MediaActionType.labelRes: Int?
    get() = when (this) {
        MediaActionType.AddToPlaylist -> R.string.menu_title_add_to_playlist
        MediaActionType.GoToAlbum -> R.string.player_go_to_album
        MediaActionType.GoToArtist -> R.string.player_go_to_artist
        MediaActionType.EditTags -> R.string.menu_title_edit_tags
        MediaActionType.SongInfo -> R.string.menu_title_song_info
        MediaActionType.Exclude -> R.string.menu_title_exclude
        else -> null
    }

private val MediaActionType.icon: ImageVector?
    get() = when (this) {
        MediaActionType.AddToPlaylist -> Icons.AutoMirrored.Rounded.PlaylistAdd
        MediaActionType.GoToAlbum -> Icons.Rounded.Album
        MediaActionType.GoToArtist -> Icons.Rounded.Person
        MediaActionType.EditTags -> Icons.Rounded.Edit
        MediaActionType.SongInfo -> Icons.Rounded.Info
        MediaActionType.Exclude -> Icons.Rounded.Block
        else -> null
    }
