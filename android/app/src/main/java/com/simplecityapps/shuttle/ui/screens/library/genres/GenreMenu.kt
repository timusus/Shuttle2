package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.library.menu.OverflowMenu
import com.simplecityapps.shuttle.ui.screens.library.menu.menu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.PersistentList

@Composable
fun GenreMenu(
    genre: Genre,
    playlists: PersistentList<Playlist>,
    onPlayGenre: (Genre) -> Unit,
    onAddToQueue: (Genre) -> Unit,
    onPlayNext: (Genre) -> Unit,
    onExclude: (Genre) -> Unit,
    onEditTags: (Genre) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    modifier: Modifier = Modifier,
    onShowCreatePlaylistDialog: (Genre) -> Unit
) {
    val menuItems = menu {
        action(
            title = { stringResource(R.string.menu_title_play) },
            onClick = { onPlayGenre(genre) }
        )
        action(
            title = { stringResource(R.string.menu_title_add_to_queue) },
            onClick = { onAddToQueue(genre) }
        )
        submenu(
            title = { stringResource(R.string.menu_title_add_to_playlist) }
        ) {
            action(
                title = { stringResource(R.string.playlist_menu_create_playlist) },
                onClick = { onShowCreatePlaylistDialog(genre) }
            )
            playlists.forEach { playlist ->
                action(
                    title = { playlist.name },
                    onClick = { onAddToPlaylist(playlist, PlaylistData.Genres(listOf(genre))) }
                )
            }
        }
        action(
            title = { stringResource(R.string.menu_title_play_next) },
            onClick = { onPlayNext(genre) }
        )
        action(
            title = { stringResource(R.string.menu_title_exclude) },
            onClick = { onExclude(genre) }
        )
        action(
            title = { stringResource(R.string.menu_title_edit_tags) },
            enabled = genre.mediaProviders.all { it.supportsTagEditing },
            onClick = { onEditTags(genre) }
        )
    }

    OverflowMenu(
        modifier = modifier,
        menuItems = menuItems
    )
}
