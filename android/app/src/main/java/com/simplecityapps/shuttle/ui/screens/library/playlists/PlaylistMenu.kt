package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.library.LibraryOverflowMenu

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
    LibraryOverflowMenu(
        contentDescription = "Playlist menu",
        modifier = modifier,
    ) {
        MenuItem(text = stringResource(id = R.string.menu_title_play)) { onPlay(playlist) }
        MenuItem(text = stringResource(id = R.string.menu_title_add_to_queue)) { onAddToQueue(playlist) }
        MenuItem(text = stringResource(id = R.string.menu_title_play_next)) { onPlayNext(playlist) }
        MenuItem(text = stringResource(id = R.string.menu_title_delete)) { onDelete(playlist) }
        MenuItem(text = stringResource(id = R.string.menu_title_clear)) { onClear(playlist) }
        MenuItem(text = stringResource(id = R.string.menu_title_rename)) { onRename(playlist) }
    }
}
