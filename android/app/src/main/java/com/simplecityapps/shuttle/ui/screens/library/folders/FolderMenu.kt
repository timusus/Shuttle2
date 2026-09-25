package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistMenuSpec
import com.simplecityapps.shuttle.ui.screens.library.LibraryOverflowMenu
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
    onShowCreatePlaylistDialog: (folder: Folder) -> Unit,
) {
    LibraryOverflowMenu(
        contentDescription = "Folder menu",
        modifier = modifier,
        addToPlaylist = AddToPlaylistMenuSpec(
            playableItem = folder,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { f -> PlaylistData.Folders(listOf(f.path)) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
        ),
    ) {
        MenuItem(text = stringResource(id = R.string.menu_title_play)) { onPlayFolder(folder) }
        MenuItem(text = stringResource(id = R.string.menu_title_shuffle)) { onShuffleFolder(folder) }
        MenuItem(text = stringResource(id = R.string.menu_title_add_to_queue)) { onAddToQueue(folder) }
        AddToPlaylistMenuItem()
        MenuItem(text = stringResource(id = R.string.menu_title_play_next)) { onPlayNext(folder) }
    }
}
