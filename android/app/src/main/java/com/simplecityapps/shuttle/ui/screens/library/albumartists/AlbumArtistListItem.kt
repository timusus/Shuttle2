package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumArtistListItem(
    albumArtist: AlbumArtist,
    isSelected: Boolean,
    playlists: ImmutableList<Playlist>,
    modifier: Modifier = Modifier,
    onClick: (AlbumArtist) -> Unit = {},
    onLongClick: (AlbumArtist) -> Unit = {},
    onPlay: (AlbumArtist) -> Unit = {},
    onAddToQueue: (AlbumArtist) -> Unit = {},
    onPlayNext: (AlbumArtist) -> Unit = {},
    onExclude: (AlbumArtist) -> Unit = {},
    onEditTags: (AlbumArtist) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onShowCreatePlaylistDialog: (AlbumArtist) -> Unit = {},
) {
    // Empty body — tests will drive implementation
}
