package com.simplecityapps.shuttle.ui.screens.library

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.mediaactions.CreatePlaylistDialog

/** A playlist management dialog: rename, or confirm clear / delete. The Playlists tab and playlist detail share them. */
sealed interface PlaylistDialog {
    val playlist: Playlist

    data class Rename(override val playlist: Playlist) : PlaylistDialog

    data class Clear(override val playlist: Playlist) : PlaylistDialog

    data class Delete(override val playlist: Playlist) : PlaylistDialog
}

/** Rename, Clear and Delete, for a playlist's actions sheet or overflow. */
fun playlistManageActions(resources: Resources, playlist: Playlist, onShow: (PlaylistDialog) -> Unit): List<S2Action> = listOf(
    S2Action(resources.getString(R.string.menu_title_rename), { onShow(PlaylistDialog.Rename(playlist)) }, Icons.Rounded.Edit),
    S2Action(resources.getString(R.string.menu_title_clear), { onShow(PlaylistDialog.Clear(playlist)) }, Icons.Rounded.ClearAll),
    S2Action(resources.getString(R.string.menu_title_delete), { onShow(PlaylistDialog.Delete(playlist)) }, Icons.Rounded.Delete, destructive = true),
)

@Composable
fun PlaylistDialogHost(
    dialog: PlaylistDialog?,
    onRename: (Playlist, String) -> Unit,
    onClear: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onDismissRequest: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        is PlaylistDialog.Rename -> CreatePlaylistDialog(
            title = stringResource(R.string.playlist_dialog_title_rename),
            initialName = dialog.playlist.name,
            onCreate = { name -> onRename(dialog.playlist, name) },
            onDismissRequest = onDismissRequest,
        )

        is PlaylistDialog.Clear -> S2Dialog(
            title = stringResource(R.string.playlist_dialog_title_clear),
            confirmLabel = stringResource(R.string.playlist_dialog_button_clear),
            dismissLabel = stringResource(R.string.dialog_button_cancel),
            destructive = true,
            onConfirm = {
                onClear(dialog.playlist)
                onDismissRequest()
            },
            onDismissRequest = onDismissRequest,
        ) {
            Text(playlistSubtitle(R.string.playlist_dialog_subtitle_clear, dialog.playlist))
        }

        is PlaylistDialog.Delete -> S2Dialog(
            title = stringResource(R.string.playlist_dialog_title_delete),
            confirmLabel = stringResource(R.string.playlist_dialog_button_delete),
            dismissLabel = stringResource(R.string.dialog_button_cancel),
            destructive = true,
            onConfirm = {
                onDelete(dialog.playlist)
                onDismissRequest()
            },
            onDismissRequest = onDismissRequest,
        ) {
            Text(playlistSubtitle(R.string.playlist_dialog_subtitle_delete, dialog.playlist))
        }
    }
}

@Composable
private fun playlistSubtitle(id: Int, playlist: Playlist): String = stringResource(id, playlist.name)
