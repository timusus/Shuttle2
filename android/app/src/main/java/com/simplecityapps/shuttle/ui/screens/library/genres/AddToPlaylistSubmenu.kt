package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData

@Composable
fun AddToPlaylistSubmenu(
    modifier: Modifier = Modifier,
    genre: Genre,
    expanded: Boolean = false,
    onDismiss: () -> Unit = {},
    playlists: List<Playlist>,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (genre: Genre) -> Unit
) {
    val playlistData = PlaylistData.Genres(genre)

    DropdownMenu(
        modifier = modifier,
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.playlist_menu_create_playlist)) },
            onClick = {
                onShowCreatePlaylistDialog(genre)
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
