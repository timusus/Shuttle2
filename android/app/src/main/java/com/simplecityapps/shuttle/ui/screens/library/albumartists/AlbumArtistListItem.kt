package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.components.SelectionMark
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class,
)
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(
            isSelected = isSelected,
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
        ) {
            GlideImage(
                model = albumArtist,
                contentDescription = stringResource(R.string.artwork),
                loading = placeholder(com.simplecityapps.core.R.drawable.ic_placeholder_artist_rounded),
            ) {
                val builder = it
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transform(CenterCrop(), RoundedCorners(8.dpToInt))
                    .transition(withCrossFade(200))
                if (artworkPreloadRequestBuilder != null) {
                    builder.thumbnail(artworkPreloadRequestBuilder)
                } else {
                    builder
                }
            }
        }
        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .combinedClickable(
                    onClick = { onClick(albumArtist) },
                    onLongClick = { onLongClick(albumArtist) },
                ),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = albumArtist.name ?: albumArtist.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
        }
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
    }
}

