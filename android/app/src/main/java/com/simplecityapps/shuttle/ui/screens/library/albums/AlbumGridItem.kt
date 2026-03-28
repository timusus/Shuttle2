package com.simplecityapps.shuttle.ui.screens.library.albums

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class,
)
@Composable
fun AlbumGridItem(
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
    Card(
        modifier = modifier.combinedClickable(
            onClick = { onClick(album) },
            onLongClick = { onLongClick(album) },
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Column {
            Box {
                GlideImage(
                    model = album,
                    contentDescription = stringResource(R.string.artwork),
                    contentScale = ContentScale.Crop,
                    loading = placeholder(com.simplecityapps.core.R.drawable.ic_placeholder_artist),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                ) {
                    val builder = it
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transform(CenterCrop())
                        .transition(withCrossFade(200))
                    if (artworkPreloadRequestBuilder != null) {
                        builder.thumbnail(artworkPreloadRequestBuilder)
                    } else {
                        builder
                    }
                }

                if (isSelected) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color(0, 0, 0, 112))
                            .padding(28.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_baseline_check_24),
                            contentDescription = stringResource(R.string.selection_mark),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                text = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                text = album.albumArtist ?: album.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
