package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumArtistList(
    uiState: AlbumArtistListUiState,
    playlists: ImmutableList<Playlist>,
    onArtistClick: (AlbumArtist) -> Unit,
    onArtistLongClick: (AlbumArtist) -> Unit,
    onPlay: (AlbumArtist) -> Unit,
    onAddToQueue: (AlbumArtist) -> Unit,
    onPlayNext: (AlbumArtist) -> Unit,
    onExclude: (AlbumArtist) -> Unit,
    onEditTags: (AlbumArtist) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (AlbumArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Empty body — tests will drive implementation
}
