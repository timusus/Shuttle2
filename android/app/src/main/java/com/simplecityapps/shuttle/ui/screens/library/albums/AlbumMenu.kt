package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistMenuSpec
import com.simplecityapps.shuttle.ui.screens.library.LibraryOverflowMenu
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
    onViewAlbum: ((Album) -> Unit)? = null,
) {
    LibraryOverflowMenu(
        contentDescription = "Album menu",
        modifier = modifier,
        addToPlaylist = AddToPlaylistMenuSpec(
            playableItem = album,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { a -> PlaylistData.Albums(a) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
        ),
    ) {
        if (onViewAlbum != null) {
            MenuItem(text = stringResource(id = R.string.menu_title_view_album)) { onViewAlbum(album) }
        }
        MenuItem(text = stringResource(id = R.string.menu_title_play)) { onPlay(album) }
        MenuItem(text = stringResource(id = R.string.menu_title_add_to_queue)) { onAddToQueue(album) }
        AddToPlaylistMenuItem()
        MenuItem(text = stringResource(id = R.string.menu_title_play_next)) { onPlayNext(album) }
        MenuItem(text = stringResource(id = R.string.menu_title_exclude)) { onExclude(album) }

        val supportsTagEditing = album.mediaProviders.all { mediaProvider ->
            mediaProvider.supportsTagEditing
        }
        if (supportsTagEditing) {
            MenuItem(text = stringResource(id = R.string.menu_title_edit_tags)) { onEditTags(album) }
        }
    }
}
