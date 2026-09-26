package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumMenu
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.DetailArtwork
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.DetailHeroImage
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.DetailSongRow
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.DiscNumberHeader
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.GroupingHeader
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.previewAlbumArtist
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.previewArtistAlbums
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.previewArtistSongs
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.previewLiveAlbum
import com.simplecityapps.shuttle.ui.screens.library.songs.SongMenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.snapshot.Snapshot
import com.simplecityapps.shuttle.ui.theme.ColorSchemePreviewParameterProvider
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AlbumArtistDetail(
    uiState: AlbumArtistDetailUiState,
    playlists: ImmutableList<Playlist>,
    onNavigateUp: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onShuffleAlbums: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onPlayAllNext: () -> Unit,
    onEditArtistTags: () -> Unit,
    onAddArtistToPlaylist: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit,
    onAlbumAddToQueue: (Album) -> Unit,
    onAlbumPlayNext: (Album) -> Unit,
    onAlbumExclude: (Album) -> Unit,
    onAlbumEditTags: (Album) -> Unit,
    onAlbumAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onAlbumShowCreatePlaylistDialog: (Album) -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumSongClick: (song: Song, songs: List<Song>) -> Unit,
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
        AlbumArtistDetailUiState.LoadingState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading))
            )
        }

        AlbumArtistDetailUiState.LoadingState.Empty -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                state = CircularLoadingState.Empty(stringResource(R.string.song_list_empty))
            )
        }

        AlbumArtistDetailUiState.LoadingState.Ready -> {
            val context = LocalContext.current
            val albumArtist = uiState.albumArtist

            DetailScaffold(
                title = albumArtist?.let { it.name ?: it.friendlyArtistName }
                    ?: stringResource(com.simplecityapps.core.R.string.unknown),
                subtitle = albumArtist?.let { albumArtistSubtitle(context, it) },
                onNavigateUp = onNavigateUp,
                hero = if (albumArtist != null) {
                    {
                        DetailHeroImage(
                            model = albumArtist,
                            placeholderResId = com.simplecityapps.core.R.drawable.ic_placeholder_artist,
                            aspectRatio = 16f / 9f,
                        )
                    }
                } else {
                    null
                },
                heroBehindTopBar = true,
                actions = {
                    ArtistOverflowMenu(
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onShuffleAlbums = onShuffleAlbums,
                        onAddToQueue = onAddAllToQueue,
                        onPlayNext = onPlayAllNext,
                        onAddToPlaylist = onAddArtistToPlaylist,
                        onEditTags = onEditArtistTags,
                        showEditTags = albumArtist?.mediaProviders?.all { it.supportsTagEditing } == true,
                    )
                },
                modifier = modifier,
            ) {
                // Metadata
                if (albumArtist != null) {
                    item {
                        AlbumArtistMetadataHeader(albumArtist = albumArtist)
                    }
                }

                // Albums section
                val albums = uiState.albums
                if (albums.isNotEmpty()) {
                    item {
                        SectionHeader(text = stringResource(R.string.albums))
                    }

                    items(albums.size, key = { albums[it].groupKey?.toString() ?: it }) { index ->
                        val album = albums[index]
                        ExpandableAlbumItem(
                            album = album,
                            songs = uiState.songsForAlbum(album),
                            expanded = album.groupKey != null && album.groupKey in uiState.expandedAlbums,
                            currentSong = uiState.currentSong,
                            playlists = playlists,
                            onClick = onAlbumClick,
                            onOpenAlbum = onOpenAlbum,
                            onPlay = onAlbumPlay,
                            onAddToQueue = onAlbumAddToQueue,
                            onPlayNext = onAlbumPlayNext,
                            onExclude = onAlbumExclude,
                            onEditTags = onAlbumEditTags,
                            onAddToPlaylist = onAlbumAddToPlaylist,
                            onShowCreatePlaylistDialog = onAlbumShowCreatePlaylistDialog,
                            onSongClick = onAlbumSongClick,
                            onSongAddToQueue = onAddToQueue,
                            onSongAddToPlaylist = onAddToPlaylist,
                            onSongShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                            onSongPlayNext = onPlayNext,
                            onSongInfo = onSongInfo,
                            onSongExclude = onExclude,
                            onSongEditTags = onEditTags,
                            onSongDelete = onDelete,
                        )
                    }
                }

                // Songs section
                val songs = uiState.songs
                if (songs.isNotEmpty()) {
                    item {
                        SectionHeader(text = stringResource(R.string.songs))
                    }

                    items(songs.size) { index ->
                        val song = songs[index]
                        AlbumArtistDetailSongItem(
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

@Composable
private fun AlbumArtistMetadataHeader(
    albumArtist: AlbumArtist,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = albumArtist.name ?: albumArtist.friendlyArtistName
                ?: stringResource(com.simplecityapps.core.R.string.unknown),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val subtitle = albumArtistSubtitle(context, albumArtist)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Album and song counts, shared by the metadata header and the collapsed top bar. */
private fun albumArtistSubtitle(
    context: Context,
    albumArtist: AlbumArtist,
): String? {
    val albumQuantity = Phrase.fromPlural(context.resources, R.plurals.albumsPlural, albumArtist.albumCount)
        .put("count", albumArtist.albumCount)
        .format()
    val songQuantity = Phrase.fromPlural(context.resources, R.plurals.songsPlural, albumArtist.songCount)
        .put("count", albumArtist.songCount)
        .format()
    return ListPhrase
        .from(" · ")
        .joinSafely(listOf(albumQuantity, songQuantity))
        ?.toString()
}

@Composable
private fun ArtistOverflowMenu(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onShuffleAlbums: () -> Unit,
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
                text = { Text(stringResource(R.string.menu_title_play)) },
                onClick = {
                    onPlay()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_title_shuffle)) },
                onClick = {
                    onShuffle()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_title_album_shuffle)) },
                onClick = {
                    onShuffleAlbums()
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
private fun SectionHeader(
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

/**
 * Album row that unfolds its own track list in place, as the shipped `ExpandableAlbumBinder` does.
 *
 * Tapping the row toggles the tracks; tapping the artwork (or the overflow menu's "View Album")
 * opens the album's own detail screen.
 */
@Composable
private fun ExpandableAlbumItem(
    album: Album,
    songs: List<Song>,
    expanded: Boolean,
    currentSong: Song?,
    playlists: ImmutableList<Playlist>,
    onClick: (Album) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit,
    onPlayNext: (Album) -> Unit,
    onExclude: (Album) -> Unit,
    onEditTags: (Album) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (Album) -> Unit,
    onSongClick: (song: Song, songs: List<Song>) -> Unit,
    onSongAddToQueue: (Song) -> Unit,
    onSongAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onSongShowCreatePlaylistDialog: (song: Song) -> Unit,
    onSongPlayNext: (Song) -> Unit,
    onSongInfo: (Song) -> Unit,
    onSongExclude: (Song) -> Unit,
    onSongEditTags: (Song) -> Unit,
    onSongDelete: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(album) }
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailArtwork(
                model = album,
                placeholderResId = com.simplecityapps.core.R.drawable.ic_placeholder_album_rounded,
                modifier = Modifier
                    .testTag("album-artwork")
                    .width(40.dp)
                    .height(40.dp)
                    .clickable { onOpenAlbum(album) },
                shape = RoundedCornerShape(8.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val songsQuantity = Phrase.fromPlural(context, R.plurals.songsPlural, album.songCount)
                    .put("count", album.songCount)
                    .format()
                val subtitle = ListPhrase.from(" · ").joinSafely(
                    listOf(album.year?.toString(), songsQuantity)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                onViewAlbum = onOpenAlbum,
                modifier = Modifier.size(40.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val discGroupingSongs = songs
                    .groupBy { it.disc ?: 1 }
                    .toSortedMap()
                    .mapValues { entry -> entry.value.groupBy { it.grouping ?: "" } }
                val hasMultipleDiscs = discGroupingSongs.size > 1

                discGroupingSongs.forEach { (discNumber, groupingMap) ->
                    if (hasMultipleDiscs) {
                        DiscNumberHeader(
                            text = Phrase.from(context, R.string.disc_number)
                                .put("disc_number", discNumber)
                                .format()
                                .toString()
                        )
                    }

                    groupingMap.forEach { (grouping, groupSongs) ->
                        if (grouping.isNotEmpty()) {
                            GroupingHeader(text = grouping)
                        }

                        groupSongs.forEach { song ->
                            DetailSongRow(
                                song = song,
                                isCurrent = song.id == currentSong?.id,
                                playlists = playlists,
                                onClick = { onSongClick(it, songs) },
                                onAddToQueue = onSongAddToQueue,
                                onAddToPlaylist = onSongAddToPlaylist,
                                onShowCreatePlaylistDialog = onSongShowCreatePlaylistDialog,
                                onPlayNext = onSongPlayNext,
                                onSongInfo = onSongInfo,
                                onExclude = onSongExclude,
                                onEditTags = onSongEditTags,
                                onDelete = onSongDelete,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtistDetailSongItem(
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
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailArtwork(
            model = song,
            placeholderResId = com.simplecityapps.core.R.drawable.ic_placeholder_song_rounded,
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = song.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
            val subtitle = ListPhrase.from(" · ").joinSafely(
                listOf(song.friendlyArtistName ?: song.albumArtist, song.album)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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
private fun ReadyWithExpandedAlbum(@PreviewParameter(ColorSchemePreviewParameterProvider::class) colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        // Inspection mode swaps artwork for a flat surface, so the snapshot never depends on image loading.
        CompositionLocalProvider(LocalInspectionMode provides true) {
            AlbumArtistDetail(
                uiState = AlbumArtistDetailUiState(
                    albumArtist = previewAlbumArtist,
                    albums = previewArtistAlbums,
                    songs = previewArtistSongs,
                    expandedAlbums = setOfNotNull<AlbumGroupKey>(previewLiveAlbum.groupKey),
                    loadingState = AlbumArtistDetailUiState.LoadingState.Ready,
                ),
                playlists = persistentListOf(),
                onNavigateUp = {},
                onPlay = {},
                onShuffle = {},
                onShuffleAlbums = {},
                onAddAllToQueue = {},
                onPlayAllNext = {},
                onEditArtistTags = {},
                onAddArtistToPlaylist = {},
                onAlbumClick = {},
                onOpenAlbum = {},
                onAlbumPlay = {},
                onAlbumAddToQueue = {},
                onAlbumPlayNext = {},
                onAlbumExclude = {},
                onAlbumEditTags = {},
                onAlbumAddToPlaylist = { _, _ -> },
                onAlbumShowCreatePlaylistDialog = {},
                onSongClick = {},
                onAlbumSongClick = { _, _ -> },
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
