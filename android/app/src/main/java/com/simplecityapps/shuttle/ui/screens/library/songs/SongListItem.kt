package com.simplecityapps.shuttle.ui.screens.library.songs

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.ListPhrase
import kotlin.collections.emptyList
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate

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
                    .transform(CenterCrop())
                    .transform(RoundedCorners(8.dpToInt))
                    // Glide ignores this in Compose for now, but not a big deal
                    .transition(withCrossFade(200))
                    .thumbnail(artworkPreloadRequestBuilder)
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

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class,
)
@Composable
private fun SelectionMark(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        content()

        if (isSelected) {
            SelectionMarkOverlay()
        }
    }
}

@Composable
private fun SelectionMarkOverlay() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0, 0, 0, 112))
            .padding(8.dp),
    ) {
        Image(
            painter = painterResource(com.simplecityapps.shuttle.R.drawable.ic_baseline_check_24),
            contentDescription = stringResource(com.simplecityapps.shuttle.R.string.selection_mark),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SongListItemPreview() {
    AppTheme(
        accent = GeneralPreferenceManager.Accent.Default
    ) {
        SongListItem(
            song = Song(
                name = "Song name",
                albumArtist = "Album artist",
                album = "Album name",
                id = 1,
                artists = emptyList(),
                duration = 1,
                genres = emptyList(),
                path = "/path",
                size = 1,
                mimeType = "ogg",
                playCount = 1,
                playbackPosition = 1,
                blacklisted = false,
                mediaProvider = MediaProviderType.Shuttle,
                track = 1,
                disc = 1,
                date = LocalDate.fromEpochDays(1),
                lastModified = Instant.fromEpochSeconds(1),
                lastPlayed = Instant.fromEpochSeconds(1),
                lastCompleted = Instant.fromEpochSeconds(1),
                lyrics = null,
                grouping = null,
                bitRate = null,
                bitDepth = null,
                sampleRate = null,
                channelCount = null,
            ),
            isSelected = true,
            playlists = emptyList<Playlist>().toImmutableList(),
            artworkPreloadRequestBuilder = Glide.with(LocalContext.current).load(null as? String)
        )
    }
}
