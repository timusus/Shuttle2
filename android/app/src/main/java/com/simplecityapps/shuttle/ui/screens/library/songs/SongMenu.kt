package com.simplecityapps.shuttle.ui.screens.library.songs

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
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistSubmenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SongMenu(
    song: Song,
    playlists: ImmutableList<Playlist>,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongInfo: (Song) -> Unit,
    onExclude: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (song: Song) -> Unit,
    modifier: Modifier = Modifier,
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
            contentDescription = stringResource(R.string.song_context_menu),
            tint = MaterialTheme.colorScheme.onBackground
        )
        DropdownMenu(
            expanded = isMenuOpened,
            onDismissRequest = { isMenuOpened = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_add_to_queue)) },
                onClick = {
                    onAddToQueue(song)
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
                    onPlayNext(song)
                    isMenuOpened = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_song_info)) },
                onClick = {
                    onSongInfo(song)
                    isMenuOpened = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_title_exclude)) },
                onClick = {
                    onExclude(song)
                    isMenuOpened = false
                },
            )

            if (song.mediaProvider.supportsTagEditing) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_edit_tags)) },
                    onClick = {
                        onEditTags(song)
                        isMenuOpened = false
                    },
                )
            }

            if (song.canBeDeleted()) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_delete)) },
                    onClick = {
                        onDelete(song)
                        isMenuOpened = false
                    }
                )
            }
        }
        AddToPlaylistSubmenu(
            playableItem = song,
            expanded = isAddToPlaylistSubmenuOpen,
            onDismiss = { isAddToPlaylistSubmenuOpen = false },
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { song -> PlaylistData.Songs(song) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog
        )
    }
}
