package com.simplecityapps.shuttle.ui.screens.library.songs

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.core.R
import com.simplecityapps.shuttle.designsystem.component.previewArtwork
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.ui.common.components.SelectionMark
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt
import com.simplecityapps.shuttle.ui.preview.SampleArtwork
import com.simplecityapps.shuttle.ui.preview.samplePlaylists
import com.simplecityapps.shuttle.ui.preview.toSong
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.ListPhrase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class,
)
@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    playlists: ImmutableList<Playlist>,
    artworkPreloadRequestBuilder: RequestBuilder<Drawable>,
    modifier: Modifier = Modifier,
    onClick: (Song) -> Unit = {},
    onLongClick: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onShowCreatePlaylistDialog: (song: Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onSongInfo: (Song) -> Unit = {},
    onExclude: (Song) -> Unit = {},
    onEditTags: (Song) -> Unit = {},
    onDelete: (Song) -> Unit = {},
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
            val preview = previewArtwork(song)
            if (preview != null) {
                Image(preview, stringResource(com.simplecityapps.shuttle.R.string.artwork), Modifier.clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else {
                GlideImage(
                    model = song,
                    contentDescription = stringResource(com.simplecityapps.shuttle.R.string.artwork),
                    loading = placeholder(R.drawable.ic_placeholder_song_rounded),
                ) {
                    // If this request finishes before than the one from the thumbnail,
                    // the result of the thumbnail one won't replace it. So, we need to
                    // repeat all options again here.
                    // TODO: Find a way to copy options from artworkPreloadRequestBuilder
                    //  to `it`. Maybe wait for the Compose API to stabilize first.
                    it
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transform(CenterCrop(), RoundedCorners(8.dpToInt))
                        .transition(withCrossFade(200))
                        .thumbnail(artworkPreloadRequestBuilder)
                }
            }
        }
        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .combinedClickable(
                    onClick = { onClick(song) },
                    onLongClick = { onLongClick(song) },
                ),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = song.name ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = ListPhrase
                    .from(" • ")
                    .joinSafely(
                        listOf(
                            song.friendlyArtistName ?: song.albumArtist,
                            song.album
                        )
                    )?.toString() ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        SongMenu(
            song = song,
            playlists = playlists,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
            onPlayNext = onPlayNext,
            onSongInfo = onSongInfo,
            onExclude = onExclude,
            onEditTags = onEditTags,
            onDelete = onDelete,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SongListItemPreview() {
    AppTheme(
        accent = Accent.Default
    ) {
        SampleArtwork {
            SongListItem(
                song = SampleLibrary.queue(1).single().toSong(),
                isSelected = true,
                playlists = samplePlaylists().toImmutableList(),
                artworkPreloadRequestBuilder = Glide.with(LocalContext.current).load(null as? String),
            )
        }
    }
}
