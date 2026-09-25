package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistMenuSpec
import com.simplecityapps.shuttle.ui.screens.library.LibraryOverflowMenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun GenreMenu(
    genre: Genre,
    playlists: ImmutableList<Playlist>,
    onPlayGenre: (Genre) -> Unit,
    onAddToQueue: (Genre) -> Unit,
    onPlayNext: (Genre) -> Unit,
    onExclude: (Genre) -> Unit,
    onEditTags: (Genre) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    modifier: Modifier = Modifier,
    onShowCreatePlaylistDialog: (genre: Genre) -> Unit,
) {
    LibraryOverflowMenu(
        contentDescription = "Genre menu",
        modifier = modifier,
        addToPlaylist = AddToPlaylistMenuSpec(
            playableItem = genre,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { g -> PlaylistData.Genres(g) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
        ),
    ) {
        MenuItem(text = stringResource(id = R.string.menu_title_play)) { onPlayGenre(genre) }
        MenuItem(text = stringResource(id = R.string.menu_title_add_to_queue)) { onAddToQueue(genre) }
        AddToPlaylistMenuItem()
        MenuItem(text = stringResource(id = R.string.menu_title_play_next)) { onPlayNext(genre) }
        MenuItem(text = stringResource(id = R.string.menu_title_exclude)) { onExclude(genre) }

        val supportsTagEditing = genre.mediaProviders.all { mediaProvider ->
            mediaProvider.supportsTagEditing
        }
        if (supportsTagEditing) {
            MenuItem(text = stringResource(id = R.string.menu_title_edit_tags)) { onEditTags(genre) }
        }
    }
}
