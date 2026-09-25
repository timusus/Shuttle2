package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.iconResId
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.components.MediaListRow

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    onPlaylistClick: (Playlist) -> Unit = {},
    onPlay: (Playlist) -> Unit = {},
    onAddToQueue: (Playlist) -> Unit = {},
    onPlayNext: (Playlist) -> Unit = {},
    onDelete: (Playlist) -> Unit = {},
    onClear: (Playlist) -> Unit = {},
    onRename: (Playlist) -> Unit = {},
) {
    MediaListRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        contentPadding = PaddingValues(start = 16.dp),
        onClick = { onPlaylistClick(playlist) },
        leading = {
            Icon(
                modifier = Modifier.size(40.dp).padding(8.dp),
                painter = painterResource(playlist.mediaProvider.iconResId()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        subtitle = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = if (playlist.songCount == 0) {
                    stringResource(R.string.song_list_empty)
                } else {
                    pluralStringResource(R.plurals.songsPlural, playlist.songCount, playlist.songCount)
                        .replace("{count}", playlist.songCount.toString())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        trailing = {
            PlaylistMenu(
                playlist = playlist,
                onPlay = onPlay,
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onDelete = onDelete,
                onClear = onClear,
                onRename = onRename,
            )
        },
    )
}
