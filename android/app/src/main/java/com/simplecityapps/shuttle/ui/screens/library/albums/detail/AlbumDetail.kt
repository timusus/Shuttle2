package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.library.songs.SongMenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
    when (uiState.loadingState) {
        AlbumDetailUiState.LoadingState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading))
            )
        }

        AlbumDetailUiState.LoadingState.Empty -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                state = CircularLoadingState.Empty(stringResource(R.string.song_list_empty))
            )
        }

        AlbumDetailUiState.LoadingState.Ready -> {
            AlbumDetailContent(
                album = uiState.album,
                songs = uiState.songs,
                currentSong = uiState.currentSong,
                playlists = playlists,
                onSongClick = onSongClick,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
                onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                onPlayNext = onPlayNext,
                onSongInfo = onSongInfo,
                onExclude = onExclude,
                onEditTags = onEditTags,
                onDelete = onDelete,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun AlbumDetailContent(
    album: Album?,
    songs: List<Song>,
    currentSong: Song?,
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
    val context = LocalContext.current
    val discGroupingSongs = songs
        .groupBy { it.disc ?: 1 }
        .toSortedMap()
        .mapValues { entry ->
            entry.value.groupBy { it.grouping ?: "" }
        }
    val hasMultipleDiscs = discGroupingSongs.size > 1

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Album metadata header
        if (album != null) {
            item {
                AlbumMetadataHeader(album = album)
            }
        }

        // Songs grouped by disc and grouping
        discGroupingSongs.forEach { (discNumber, groupingMap) ->
            if (hasMultipleDiscs) {
                item {
                    DiscNumberHeader(
                        text = Phrase.from(context, R.string.disc_number)
                            .put("disc_number", discNumber)
                            .format()
                            .toString()
                    )
                }
            }

            groupingMap.forEach { (grouping, groupSongs) ->
                if (grouping.isNotEmpty()) {
                    item {
                        GroupingHeader(text = grouping)
                    }
                }

                items(groupSongs.size) { index ->
                    val song = groupSongs[index]
                    AlbumDetailSongItem(
                        song = song,
                        isCurrent = song.id == currentSong?.id,
                        playlists = playlists,
                        onClick = onSongClick,
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
        }
    }
}

@Composable
private fun AlbumMetadataHeader(
    album: Album,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val songsQuantity = Phrase.fromPlural(context.resources, R.plurals.songsPlural, album.songCount)
            .put("count", album.songCount)
            .format()
        val subtitle = ListPhrase
            .from(" · ")
            .joinSafely(
                listOf(
                    album.year?.toString(),
                    songsQuantity,
                    album.duration.toHms(),
                )
            )
        if (subtitle != null) {
            Text(
                text = subtitle.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscNumberHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun GroupingHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun AlbumDetailSongItem(
    song: Song,
    isCurrent: Boolean,
    playlists: ImmutableList<Playlist>,
    onClick: (Song) -> Unit,
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
    val highlightModifier = if (isCurrent) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .semantics { contentDescription = "Now playing" }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(highlightModifier)
            .clickable { onClick(song) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Track number
        Text(
            text = song.track?.toString() ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )

        // Song title
        Text(
            text = song.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            modifier = Modifier.weight(1f),
        )

        // Duration
        Text(
            text = song.duration.toHms("--:--"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Context menu
        SongMenu(
            song = song,
            playlists = playlists.toImmutableList(),
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
