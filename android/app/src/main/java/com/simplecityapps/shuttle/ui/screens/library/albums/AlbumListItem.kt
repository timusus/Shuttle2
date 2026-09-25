package com.simplecityapps.shuttle.ui.screens.library.albums

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.RequestBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.components.LibraryArtworkGlideImage
import com.simplecityapps.shuttle.ui.common.components.MediaListRow
import com.simplecityapps.shuttle.ui.common.components.SelectionMark
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumListItem(
    album: Album,
    isSelected: Boolean,
    playlists: ImmutableList<Playlist>,
    modifier: Modifier = Modifier,
    artworkPreloadRequestBuilder: RequestBuilder<Drawable>? = null,
    onClick: (Album) -> Unit = {},
    onLongClick: (Album) -> Unit = {},
    onPlay: (Album) -> Unit = {},
    onAddToQueue: (Album) -> Unit = {},
    onPlayNext: (Album) -> Unit = {},
    onExclude: (Album) -> Unit = {},
    onEditTags: (Album) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onShowCreatePlaylistDialog: (Album) -> Unit = {},
) {
    MediaListRow(
        modifier = modifier,
        onClick = { onClick(album) },
        onLongClick = { onLongClick(album) },
        leading = {
            SelectionMark(
                isSelected = isSelected,
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp),
            ) {
                LibraryArtworkGlideImage(
                    model = album,
                    placeholderDrawableResId = com.simplecityapps.core.R.drawable.ic_placeholder_artist_rounded,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                )
            }
        },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        subtitle = {
            val artistName = album.albumArtist ?: album.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
            val songCount = pluralStringResource(R.plurals.songsPlural, album.songCount, album.songCount)
                .replace("{count}", album.songCount.toString())
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "$artistName · $songCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        trailing = {
            AlbumMenu(
                album = album,
                playlists = playlists,
                onPlay = onPlay,
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onExclude = onExclude,
                onEditTags = onEditTags,
                onAddToPlaylist = onAddToPlaylist,
                onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
            )
        },
    )
}
