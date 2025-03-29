package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData

@Composable
fun AddToPlaylistSubmenu(
    song: Song,
    playlists: List<Playlist>,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (song: Song) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onDismiss: () -> Unit = {},
) {
    val playlistData = PlaylistData.Songs(song)

    DropdownMenu(
        modifier = modifier,
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.playlist_menu_create_playlist)) },
            onClick = {
                onShowCreatePlaylistDialog(song)
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
