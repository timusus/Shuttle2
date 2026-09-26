package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.designsystem.component.previewArtwork
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.screens.library.songs.SongMenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Largest hero edge we allow once the window is wide or short, so tablets and landscape don't get one giant image. */
private val HeroMaxHeight = 360.dp

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

            DetailScaffold(
                title = album?.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                subtitle = album?.let { albumSubtitle(context, it) },
                onNavigateUp = onNavigateUp,
                hero = if (album != null) {
                    {
                        DetailHeroImage(
                            model = album,
                            placeholderResId = com.simplecityapps.core.R.drawable.ic_placeholder_album,
                            aspectRatio = 1f,
                        )
                    }
                } else {
                    null
                },
                heroBehindTopBar = true,
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
                // Metadata
                if (album != null) {
                    item {
                        AlbumMetadataHeader(album = album)
                    }
                }

                // Songs
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
                            DetailSongRow(
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

/**
 * Full-width artwork, rendered as a plain list item so it scrolls away with the content.
 *
 * On a phone in portrait the image runs edge to edge; on a wide or short window it is capped at
 * [HeroMaxHeight] and centred, rather than filling the entire viewport.
 */
@Composable
internal fun DetailHeroImage(
    model: Any?,
    placeholderResId: Int,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isWideOrShort = configuration.screenWidthDp >= 600 || configuration.screenHeightDp < 600

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        DetailArtwork(
            model = model,
            placeholderResId = placeholderResId,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isWideOrShort) Modifier.heightIn(max = HeroMaxHeight) else Modifier)
                .aspectRatio(aspectRatio),
        )
    }
}

/**
 * Artwork loaded through Coil, cross-fading in over its placeholder, with a flat surface standing in under inspection mode.
 *
 * Previews and snapshot tests never load an image, and the placeholder drawables resolve a
 * theme attribute that layoutlib cannot inflate, so the request is skipped entirely there.
 */
@Composable
internal fun DetailArtwork(
    model: Any?,
    placeholderResId: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    val artworkDescription = stringResource(R.string.artwork)
    val preview = previewArtwork(model)
    if (preview != null) {
        Image(preview, artworkDescription, modifier.clip(shape), contentScale = ContentScale.Crop)
        return
    }
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = artworkDescription },
        )
        return
    }
    // The placeholders are layer-list drawables, which painterResource can't load, so the request inflates them
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .placeholder(placeholderResId)
            .error(placeholderResId)
            .crossfade(DETAIL_ARTWORK_CROSSFADE_MILLIS)
            .build(),
        contentDescription = artworkDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
    )
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
            .padding(16.dp),
    ) {
        Text(
            text = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val subtitle = albumSubtitle(context, album)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Year, song count and duration, shared by the metadata header and the collapsed top bar. */
private fun albumSubtitle(
    context: Context,
    album: Album,
): String? {
    val songsQuantity = Phrase.fromPlural(context.resources, R.plurals.songsPlural, album.songCount)
        .put("count", album.songCount)
        .format()
    return ListPhrase
        .from(" · ")
        .joinSafely(
            listOf(
                album.year?.toString(),
                songsQuantity,
                formatDuration(album.duration.toLong(), padded = true),
            )
        )
        ?.toString()
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
internal fun DiscNumberHeader(
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
internal fun GroupingHeader(
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

/**
 * Track-number / title / duration / overflow row, matching the density of the shipped
 * `list_item_detail_song` layout (48dp minimum height, 16dp leading inset).
 *
 * Shared with the expanded albums on the album artist detail screen.
 */
@Composable
internal fun DetailSongRow(
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
            .testTag("detail-song-row")
            .then(highlightModifier)
            .clickable { onClick(song) }
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

        Text(
            text = formatDuration(song.duration.toLong(), zeroValue = "--:--", padded = true),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
            modifier = Modifier.size(40.dp),
        )
    }
}

@Snapshot
@Preview
@Composable
private fun Ready(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        // Inspection mode swaps artwork for a flat surface, so the snapshot never depends on image loading.
        CompositionLocalProvider(LocalInspectionMode provides true) {
            AlbumDetail(
                uiState = AlbumDetailUiState(
                    album = previewAlbum,
                    songs = previewAlbumSongs,
                    currentSong = previewAlbumSongs[1],
                    loadingState = AlbumDetailUiState.LoadingState.Ready,
                ),
                playlists = persistentListOf(),
                onNavigateUp = {},
                onShuffle = {},
                onAddAlbumToQueue = {},
                onPlayAlbumNext = {},
                onEditAlbumTags = {},
                onAddAlbumToPlaylist = {},
                onSongClick = {},
                onAddToQueue = {},
                onAddToPlaylist = { _, _ -> },
                onShowCreatePlaylistDialog = {},
                onPlayNext = {},
                onSongInfo = {},
                onExclude = {},
                onEditTags = {},
                onDelete = {},
            )
        }
    }
}

private const val DETAIL_ARTWORK_CROSSFADE_MILLIS = 200
