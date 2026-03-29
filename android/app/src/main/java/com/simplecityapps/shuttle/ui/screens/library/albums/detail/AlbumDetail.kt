package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.CollapsingHeroScaffold
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.library.songs.SongMenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AlbumDetail(
    uiState: AlbumDetailUiState,
    playlists: ImmutableList<Playlist>,
    onNavigateUp: () -> Unit,
    onShuffle: () -> Unit,
    onAddAlbumToQueue: () -> Unit,
    onPlayAlbumNext: () -> Unit,
    onEditAlbumTags: () -> Unit,
    onAddAlbumToPlaylist: () -> Unit,
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
            val context = LocalContext.current
            val album = uiState.album
            val toolbarTitle = album?.name ?: ""
            val songsQuantity = album?.let {
                Phrase.fromPlural(context.resources, R.plurals.songsPlural, it.songCount)
                    .put("count", it.songCount)
                    .format()
            }
            val toolbarSubtitle = album?.let {
                ListPhrase.from(" \u00B7 ").joinSafely(
                    listOf(it.year?.toString(), songsQuantity, it.duration.toHms())
                )?.toString()
            }

            CollapsingHeroScaffold(
                heroContent = { _ ->
                    GlideImage(
                        model = album,
                        contentDescription = stringResource(R.string.artwork),
                        contentScale = ContentScale.Crop,
                        loading = placeholder(com.simplecityapps.core.R.drawable.ic_placeholder_album),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        it
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .transition(withCrossFade(200))
                    }
                },
                title = toolbarTitle,
                subtitle = toolbarSubtitle,
                onNavigateUp = onNavigateUp,
                actions = {
                    AlbumDetailOverflowMenu(
                        onShuffle = onShuffle,
                        onAddToQueue = onAddAlbumToQueue,
                        onPlayNext = onPlayAlbumNext,
                        onAddToPlaylist = onAddAlbumToPlaylist,
                        onEditTags = onEditAlbumTags,
                        showEditTags = album?.mediaProviders?.all { it.supportsTagEditing } == true,
                    )
                },
                modifier = modifier,
            ) {
                val songs = uiState.songs
                val discGroupingSongs = songs
                    .groupBy { it.disc ?: 1 }
                    .toSortedMap()
                    .mapValues { entry ->
                        entry.value.groupBy { it.grouping ?: "" }
                    }
                val hasMultipleDiscs = discGroupingSongs.size > 1

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
                                isCurrent = song.id == uiState.currentSong?.id,
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
    }
}

@Composable
private fun AlbumDetailOverflowMenu(
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    showEditTags: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_title_shuffle)) },
                onClick = {
                    onShuffle()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_title_add_to_queue)) },
                onClick = {
                    onAddToQueue()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_title_add_to_playlist)) },
                onClick = {
                    onAddToPlaylist()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_title_play_next)) },
                onClick = {
                    onPlayNext()
                    expanded = false
                },
            )
            if (showEditTags) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_title_edit_tags)) },
                    onClick = {
                        onEditTags()
                        expanded = false
                    },
                )
            }
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
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
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
