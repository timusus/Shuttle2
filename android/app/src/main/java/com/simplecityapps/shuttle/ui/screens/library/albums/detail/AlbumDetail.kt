package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumDetail(
    uiState: AlbumDetailUiState,
    playlists: ImmutableList<Playlist>,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (song: Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongInfo: (Song) -> Unit,
    onExclude: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier)
}
