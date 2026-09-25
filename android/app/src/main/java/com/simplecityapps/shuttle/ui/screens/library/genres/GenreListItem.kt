package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.ui.common.components.MediaListRow
import com.simplecityapps.shuttle.ui.preview.samplePlaylists
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun GenreListItem(
    genre: Genre,
    playlists: ImmutableList<Playlist>,
    modifier: Modifier = Modifier,
    onSelectGenre: (genre: Genre) -> Unit = {},
    onPlayGenre: (Genre) -> Unit = {},
    onAddToQueue: (Genre) -> Unit = {},
    onPlayNext: (Genre) -> Unit = {},
    onExclude: (Genre) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onEditTags: (Genre) -> Unit = {},
    onShowCreatePlaylistDialog: (genre: Genre) -> Unit = {},
) {
    MediaListRow(
        modifier = modifier,
        onClick = { onSelectGenre(genre) },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = genre.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        subtitle = {
            // Todo: Manually replacing "{count}" is not ideal. But, the Phrase library doesn't render correctly in Compose.
            //  Will need to come up with a better solution.
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = pluralStringResource(R.plurals.songsPlural, genre.songCount, genre.songCount)
                    .replace("{count}", genre.songCount.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        trailing = {
            GenreMenu(
                genre,
                playlists = playlists,
                onPlayGenre = onPlayGenre,
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onExclude = onExclude,
                onEditTags = onEditTags,
                onAddToPlaylist = onAddToPlaylist,
                onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun GenreListItemPreview() {
    AppTheme(
        accent = Accent.Default,
    ) {
        GenreListItem(
            genre = SampleLibrary.genres.first().toGenre(),
            playlists = samplePlaylists().toImmutableList(),
        )
    }
}
