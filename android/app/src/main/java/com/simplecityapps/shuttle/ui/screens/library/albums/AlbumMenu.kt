package com.simplecityapps.shuttle.ui.screens.library.albums

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
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistSubmenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumMenu(
    album: Album,
    playlists: ImmutableList<Playlist>,
    onPlay: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit,
    onPlayNext: (Album) -> Unit,
    onExclude: (Album) -> Unit,
    onEditTags: (Album) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    modifier: Modifier = Modifier,
    onShowCreatePlaylistDialog: (Album) -> Unit,
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
            contentDescription = "Album menu",
            tint = MaterialTheme.colorScheme.onBackground
        )
        DropdownMenu(
            expanded = isMenuOpened,
            onDismissRequest = { isMenuOpened = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_play)) },
                onClick = {
                    onPlay(album)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_add_to_queue)) },
                onClick = {
                    onAddToQueue(album)
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
                    onPlayNext(album)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_exclude)) },
                onClick = {
                    onExclude(album)
                    isMenuOpened = false
                }
            )

            val supportsTagEditing = album.mediaProviders.all { mediaProvider ->
                mediaProvider.supportsTagEditing
            }

            if (supportsTagEditing) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_edit_tags)) },
                    onClick = {
                        onEditTags(album)
                        isMenuOpened = false
                    }
                )
            }
        }
        AddToPlaylistSubmenu(
            playableItem = album,
            expanded = isAddToPlaylistSubmenuOpen,
            onDismiss = { isAddToPlaylistSubmenuOpen = false },
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { a -> PlaylistData.Albums(a) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
        )
    }
}
