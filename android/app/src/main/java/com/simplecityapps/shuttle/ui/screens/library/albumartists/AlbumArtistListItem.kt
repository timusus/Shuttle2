package com.simplecityapps.shuttle.ui.screens.library.albumartists

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
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.components.LibraryArtworkGlideImage
import com.simplecityapps.shuttle.ui.common.components.MediaListRow
import com.simplecityapps.shuttle.ui.common.components.SelectionMark
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AlbumArtistListItem(
    albumArtist: AlbumArtist,
    isSelected: Boolean,
    playlists: ImmutableList<Playlist>,
    modifier: Modifier = Modifier,
    artworkPreloadRequestBuilder: RequestBuilder<Drawable>? = null,
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
    MediaListRow(
        modifier = modifier,
        onClick = { onClick(albumArtist) },
        onLongClick = { onLongClick(albumArtist) },
        leading = {
            SelectionMark(
                isSelected = isSelected,
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp),
            ) {
                LibraryArtworkGlideImage(
                    model = albumArtist,
                    placeholderDrawableResId = com.simplecityapps.core.R.drawable.ic_placeholder_artist_rounded,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                )
            }
        },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = albumArtist.name ?: albumArtist.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        subtitle = {
            val albumCount = pluralStringResource(R.plurals.albumsPlural, albumArtist.albumCount, albumArtist.albumCount)
                .replace("{count}", albumArtist.albumCount.toString())
            val songCount = pluralStringResource(R.plurals.songsPlural, albumArtist.songCount, albumArtist.songCount)
                .replace("{count}", albumArtist.songCount.toString())
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "$albumCount · $songCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        trailing = {
            AlbumArtistMenu(
                albumArtist = albumArtist,
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
