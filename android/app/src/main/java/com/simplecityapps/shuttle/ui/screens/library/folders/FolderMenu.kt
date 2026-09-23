package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistSubmenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

/** Actions on a folder apply to the songs in it and all its subfolders. */
@Composable
fun FolderMenu(
    folder: Folder,
    playlists: ImmutableList<Playlist>,
    onPlayFolder: (Folder) -> Unit,
    onShuffleFolder: (Folder) -> Unit,
    onAddToQueue: (Folder) -> Unit,
    onPlayNext: (Folder) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    modifier: Modifier = Modifier,
    onShowCreatePlaylistDialog: (folder: Folder) -> Unit
) {
    var isMenuOpened by remember { mutableStateOf(false) }
    var isAddToPlaylistSubmenuOpen by remember { mutableStateOf(false) }

    IconButton(
        modifier = modifier,
        onClick = { isMenuOpened = true }
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Folder menu",
            tint = MaterialTheme.colorScheme.onBackground
        )
        DropdownMenu(
            expanded = isMenuOpened,
            onDismissRequest = { isMenuOpened = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_play)) },
                onClick = {
                    onPlayFolder(folder)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_shuffle)) },
                onClick = {
                    onShuffleFolder(folder)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_add_to_queue)) },
                onClick = {
                    onAddToQueue(folder)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_add_to_playlist)) },
                onClick = {
                    isMenuOpened = false
                    isAddToPlaylistSubmenuOpen = true
                },
                trailingIcon = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_play_next)) },
                onClick = {
                    onPlayNext(folder)
                    isMenuOpened = false
                }
            )
        }
        AddToPlaylistSubmenu(
            playableItem = folder,
            expanded = isAddToPlaylistSubmenuOpen,
            onDismiss = { isAddToPlaylistSubmenuOpen = false },
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { folder -> PlaylistData.Folders(listOf(folder.path)) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog
        )
    }
}
