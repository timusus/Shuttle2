package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumMenu
import com.simplecityapps.shuttle.ui.screens.library.songs.SongMenu
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalGlideComposeApi::class)
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
    onAlbumPlay: (Album) -> Unit,
    onAlbumAddToQueue: (Album) -> Unit,
    onAlbumPlayNext: (Album) -> Unit,
    onAlbumExclude: (Album) -> Unit,
    onAlbumEditTags: (Album) -> Unit,
    onAlbumAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onAlbumShowCreatePlaylistDialog: (Album) -> Unit,
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
                onNavigateUp = onNavigateUp,
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
                // Artwork
                if (albumArtist != null) {
                    item {
                        GlideImage(
                            model = albumArtist,
                            contentDescription = stringResource(R.string.artwork),
                            contentScale = ContentScale.Crop,
                            loading = placeholder(com.simplecityapps.core.R.drawable.ic_placeholder_artist),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        ) {
                            it
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .transition(withCrossFade(200))
                        }
                    }

                    // Metadata
                    item {
                        val albumQuantity = Phrase.fromPlural(
                            context.resources,
                            R.plurals.albumsPlural,
                            albumArtist.albumCount
                        )
                            .put("count", albumArtist.albumCount)
                            .format()
                        val songQuantity = Phrase.fromPlural(
                            context.resources,
                            R.plurals.songsPlural,
                            albumArtist.songCount
                        )
                            .put("count", albumArtist.songCount)
                            .format()
                        val subtitle = ListPhrase.from(" \u00B7 ")
                            .joinSafely(listOf(albumQuantity, songQuantity))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = albumArtist.name ?: albumArtist.friendlyArtistName
                                    ?: stringResource(com.simplecityapps.core.R.string.unknown),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
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
                }

                // Albums section
                val albums = uiState.albums
                if (albums.isNotEmpty()) {
                    item {
                        SectionHeader(text = stringResource(R.string.albums))
                    }

                    items(albums.size) { index ->
                        val album = albums[index]
                        AlbumArtistDetailAlbumItem(
                            album = album,
                            playlists = playlists,
                            onClick = onAlbumClick,
                            onPlay = onAlbumPlay,
                            onAddToQueue = onAlbumAddToQueue,
                            onPlayNext = onAlbumPlayNext,
                            onExclude = onAlbumExclude,
                            onEditTags = onAlbumEditTags,
                            onAddToPlaylist = onAlbumAddToPlaylist,
                            onShowCreatePlaylistDialog = onAlbumShowCreatePlaylistDialog,
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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumArtistDetailAlbumItem(
    album: Album,
    playlists: ImmutableList<Playlist>,
    onClick: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit,
    onPlayNext: (Album) -> Unit,
    onExclude: (Album) -> Unit,
    onEditTags: (Album) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(album) }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlideImage(
            model = album,
            contentDescription = stringResource(R.string.artwork),
            loading = placeholder(com.simplecityapps.core.R.drawable.ic_placeholder_album_rounded),
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
        ) {
            it
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(CenterCrop(), RoundedCorners(8.dpToInt))
                .transition(withCrossFade(200))
        }

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
            val subtitle = ListPhrase.from(" \u00B7 ").joinSafely(
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
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
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
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlideImage(
            model = song,
            contentDescription = stringResource(R.string.artwork),
            loading = placeholder(com.simplecityapps.core.R.drawable.ic_placeholder_song_rounded),
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
        ) {
            it
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(CenterCrop(), RoundedCorners(8.dpToInt))
                .transition(withCrossFade(200))
        }

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
            val subtitle = ListPhrase.from(" \u00B7 ").joinSafely(
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
            text = song.duration.toHms("--:--"),
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
        )
    }
}
