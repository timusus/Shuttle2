package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun <T, R : PlaylistData> AddToPlaylistSubmenu(
    playableItem: T,
    playlists: ImmutableList<Playlist>,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    playlistDataCreator: (playableItem: T) -> R,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onDismiss: () -> Unit = {},
    onShowCreatePlaylistDialog: (playableItem: T) -> Unit,
) {
    val playlistData = playlistDataCreator(playableItem)

    DropdownMenu(
        modifier = modifier,
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.playlist_menu_create_playlist)) },
            onClick = {
                onShowCreatePlaylistDialog(playableItem)
                onDismiss()
            }
        )

        for (playlist in playlists) {
            DropdownMenuItem(
                text = { Text(playlist.name) },
                onClick = {
                    onAddToPlaylist(playlist, playlistData)
                    onDismiss()
                }
            )
        }
    }
}
