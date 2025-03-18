package com.simplecityapps.shuttle.ui.screens.library.songs

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.core.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.ListPhrase
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    onAddToQueue: (Song) -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = song.name ?: "no name", // FIXME
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = ListPhrase
                    .from(" • ")
                    .joinSafely(
                        listOf(
                            song.friendlyArtistName ?: song.albumArtist,
                            song.album
                        )
                    )?.toString() ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        SongMenu(
            song = song,
            onAddToQueue = onAddToQueue,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SongListItemPreview() {
    AppTheme(
        accent = GeneralPreferenceManager.Accent.Default
    ) {
        SongListItem(
            song = Song(
                name = "Song name",
                albumArtist = "Album artist",
                album = "Album name",
                id = 1,
                artists = emptyList(),
                duration = 1,
                genres = emptyList(),
                path = "/path",
                size = 1,
                mimeType = "ogg",
                playCount = 1,
                playbackPosition = 1,
                blacklisted = false,
                mediaProvider = MediaProviderType.Shuttle,
                track = 1,
                disc = 1,
                date = LocalDate.fromEpochDays(1),
                lastModified = Instant.fromEpochSeconds(1),
                lastPlayed = Instant.fromEpochSeconds(1),
                lastCompleted = Instant.fromEpochSeconds(1),
                lyrics = null,
                grouping = null,
                bitRate = null,
                bitDepth = null,
                sampleRate = null,
                channelCount = null,
            ),
        )
    }
}
