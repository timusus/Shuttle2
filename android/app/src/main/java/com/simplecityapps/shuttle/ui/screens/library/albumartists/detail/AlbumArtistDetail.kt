package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumArtistDetail(
    uiState: AlbumArtistDetailUiState,
    playlists: ImmutableList<Playlist>,
    onAlbumClick: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit,
    onAlbumAddToQueue: (Album) -> Unit,
    onAlbumPlayNext: (Album) -> Unit,
    onAlbumExclude: (Album) -> Unit,
    onAlbumEditTags: (Album) -> Unit,
    onAlbumAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onAlbumShowCreatePlaylistDialog: (Album) -> Unit,
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
