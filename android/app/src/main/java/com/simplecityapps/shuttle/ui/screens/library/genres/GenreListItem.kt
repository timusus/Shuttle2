package com.simplecityapps.shuttle.ui.screens.library.genres

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.compose.ui.theme.AppTheme
import com.simplecityapps.shuttle.compose.ui.theme.ThemeAccent
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun GenreListItem(
    genre: Genre,
    playlists: PersistentList<Playlist>,
    modifier: Modifier = Modifier,
    onSelectGenre: (genre: Genre) -> Unit = {},
    onPlayGenre: (Genre) -> Unit = {},
    onAddToQueue: (Genre) -> Unit = {},
    onPlayNext: (Genre) -> Unit = {},
    onExclude: (Genre) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onEditTags: (Genre) -> Unit = {},
    onShowCreatePlaylistDialog: (genre: Genre) -> Unit = {}
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .clickable { onSelectGenre(genre) }
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = genre.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            // Todo: Manually replacing "{count}" is not ideal. But, the Phrase library doesn't render correctly in Compose.
            //  Will need to come up with a better solution.
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = pluralStringResource(R.plurals.songsPlural, genre.songCount, genre.songCount)
                    .replace("{count}", genre.songCount.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        GenreMenu(
            genre,
            playlists = playlists,
            onPlayGenre = onPlayGenre,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onExclude = onExclude,
            onEditTags = onEditTags,
            onAddToPlaylist = onAddToPlaylist,
            onShowCreatePlaylistDialog = onShowCreatePlaylistDialog
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun GenreListItemPreview() {
    AppTheme(
        accent = ThemeAccent.Default
    ) {
        GenreListItem(
            genre = Genre(
                name = "Genre",
                songCount = 1,
                duration = 10,
                mediaProviders = listOf(MediaProviderType.MediaStore)
            ),
            playlists = persistentListOf(
                Playlist(
                    id = 1,
                    name = "Playlist",
                    songCount = 1,
                    duration = 10,
                    sortOrder = PlaylistSongSortOrder.SongName,
                    mediaProvider = MediaProviderType.MediaStore,
                    externalId = null
                )
            )
        )
    }
}
