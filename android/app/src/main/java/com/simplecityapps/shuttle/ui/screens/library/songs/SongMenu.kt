package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.library.AddToPlaylistMenuSpec
import com.simplecityapps.shuttle.ui.screens.library.LibraryOverflowMenu
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
    LibraryOverflowMenu(
        contentDescription = stringResource(R.string.song_context_menu),
        modifier = modifier,
        addToPlaylist = AddToPlaylistMenuSpec(
            playableItem = song,
            playlists = playlists,
            onAddToPlaylist = onAddToPlaylist,
            playlistDataCreator = { s -> PlaylistData.Songs(s) },
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
        ),
    ) {
        MenuItem(text = stringResource(id = R.string.menu_title_add_to_queue)) { onAddToQueue(song) }
        AddToPlaylistMenuItem()
        MenuItem(text = stringResource(id = R.string.menu_title_play_next)) { onPlayNext(song) }
        MenuItem(text = stringResource(id = R.string.menu_title_song_info)) { onSongInfo(song) }
        MenuItem(text = stringResource(id = R.string.menu_title_exclude)) { onExclude(song) }

        if (song.mediaProvider.supportsTagEditing) {
            MenuItem(text = stringResource(id = R.string.menu_title_edit_tags)) { onEditTags(song) }
        }

        if (song.canBeDeleted()) {
            MenuItem(text = stringResource(id = R.string.menu_title_delete)) { onDelete(song) }
        }
    }
}
