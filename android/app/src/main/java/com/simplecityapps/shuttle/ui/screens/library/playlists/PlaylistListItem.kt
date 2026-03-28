package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.iconResId
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist

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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(40.dp).padding(8.dp),
            painter = painterResource(playlist.mediaProvider.iconResId()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground
        )
        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f)
                .clickable { onPlaylistClick(playlist) }
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = if (playlist.songCount == 0) {
                    stringResource(R.string.song_list_empty)
                } else {
                    pluralStringResource(R.plurals.songsPlural, playlist.songCount, playlist.songCount)
                        .replace("{count}", playlist.songCount.toString())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        PlaylistMenu(
            playlist = playlist,
            onPlay = onPlay,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onDelete = onDelete,
            onClear = onClear,
            onRename = onRename,
        )
    }
}
