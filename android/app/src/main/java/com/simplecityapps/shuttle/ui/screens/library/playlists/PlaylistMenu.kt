package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist

@Composable
fun PlaylistMenu(
    playlist: Playlist,
    onPlay: (Playlist) -> Unit,
    onAddToQueue: (Playlist) -> Unit,
    onPlayNext: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onClear: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuOpened by remember { mutableStateOf(false) }

    IconButton(
        modifier = modifier,
        onClick = { isMenuOpened = true }
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Playlist menu",
            tint = MaterialTheme.colorScheme.onBackground
        )
        DropdownMenu(
            expanded = isMenuOpened,
            onDismissRequest = { isMenuOpened = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_play)) },
                onClick = {
                    onPlay(playlist)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_add_to_queue)) },
                onClick = {
                    onAddToQueue(playlist)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_play_next)) },
                onClick = {
                    onPlayNext(playlist)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_delete)) },
                onClick = {
                    onDelete(playlist)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_clear)) },
                onClick = {
                    onClear(playlist)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_rename)) },
                onClick = {
                    onRename(playlist)
                    isMenuOpened = false
                }
            )
        }
    }
}
