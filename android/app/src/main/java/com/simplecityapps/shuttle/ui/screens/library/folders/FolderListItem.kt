package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun FolderListItem(
    folder: Folder,
    playlists: ImmutableList<Playlist>,
    modifier: Modifier = Modifier,
    onFolderClick: (Folder) -> Unit = {},
    onPlayFolder: (Folder) -> Unit = {},
    onShuffleFolder: (Folder) -> Unit = {},
    onAddToQueue: (Folder) -> Unit = {},
    onPlayNext: (Folder) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onShowCreatePlaylistDialog: (folder: Folder) -> Unit = {}
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp),
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground
        )
        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .clickable { onFolderClick(folder) }
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = folder.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = pluralStringResource(R.plurals.songsPlural, folder.songCount, folder.songCount)
                    .replace("{count}", folder.songCount.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        FolderMenu(
            folder = folder,
            playlists = playlists,
            onPlayFolder = onPlayFolder,
            onShuffleFolder = onShuffleFolder,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog
        )
    }
}

@PreviewLightDark
@Composable
private fun FolderListItemPreview() {
    AppTheme(
        accent = Accent.Default
    ) {
        FolderListItem(
            folder = Folder(path = listOf("primary", "Music", "Radiohead"), songCount = 42),
            playlists = persistentListOf()
        )
    }
}
