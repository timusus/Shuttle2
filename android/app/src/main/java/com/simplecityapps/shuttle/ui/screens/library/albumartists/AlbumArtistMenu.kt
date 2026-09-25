package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistMenuSpec
import com.simplecityapps.shuttle.ui.screens.library.LibraryOverflowMenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumArtistMenu(
    albumArtist: AlbumArtist,
    playlists: ImmutableList<Playlist>,
    onPlay: (AlbumArtist) -> Unit,
    onAddToQueue: (AlbumArtist) -> Unit,
    onPlayNext: (AlbumArtist) -> Unit,
    onExclude: (AlbumArtist) -> Unit,
    onEditTags: (AlbumArtist) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    modifier: Modifier = Modifier,
    onShowCreatePlaylistDialog: (AlbumArtist) -> Unit,
) {
    LibraryOverflowMenu(
        contentDescription = "Album artist menu",
        modifier = modifier,
        addToPlaylist = AddToPlaylistMenuSpec(
            playableItem = albumArtist,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { artist -> PlaylistData.AlbumArtists(artist) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
        ),
    ) {
        MenuItem(text = stringResource(id = R.string.menu_title_play)) { onPlay(albumArtist) }
        MenuItem(text = stringResource(id = R.string.menu_title_add_to_queue)) { onAddToQueue(albumArtist) }
        AddToPlaylistMenuItem()
        MenuItem(text = stringResource(id = R.string.menu_title_play_next)) { onPlayNext(albumArtist) }
        MenuItem(text = stringResource(id = R.string.menu_title_exclude)) { onExclude(albumArtist) }

        val supportsTagEditing = albumArtist.mediaProviders.all { mediaProvider ->
            mediaProvider.supportsTagEditing
        }
        if (supportsTagEditing) {
            MenuItem(text = stringResource(id = R.string.menu_title_edit_tags)) { onEditTags(albumArtist) }
        }
    }
}
