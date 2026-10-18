package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.MovementRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SectionHeaderStyle
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.component.WorkHeaderRow
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.downloads.offlineState
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumRow
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.WorkTap
import com.simplecityapps.shuttle.ui.text.stringResource

internal fun LazyListScope.classicalRows(
    rows: List<AlbumRow>,
    album: Album?,
    songs: List<Song>,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onPlayWork: (songs: List<Song>) -> Unit,
    onToggleWork: (firstSongId: Long) -> Unit,
    onWorkMore: (title: String, songs: List<Song>) -> Unit,
    onOtherRecordings: (firstSongId: Long) -> Unit,
    onSongMore: (Song) -> Unit,
    onOpenComposer: (composer: String) -> Unit,
) {
    items(rows, key = { it.key }, contentType = { it::class }) { row ->
        when (row) {
            is AlbumRow.ComposerHeading -> {
                val composer = row.composer
                val label = stringResource(R.string.composer_open, row.name)
                SectionHeader(
                    title = row.name,
                    modifier = if (composer == null) {
                        Modifier
                    } else {
                        Modifier.clickable(role = Role.Button) { onOpenComposer(composer) }.semantics { contentDescription = label }
                    },
                )
            }

            is AlbumRow.ActHeader -> SectionHeader(title = row.label, style = SectionHeaderStyle.Label)

            is AlbumRow.DiscDivider -> SectionHeader(title = stringResource(R.string.album_detail_disc, row.disc))

            is AlbumRow.WorkHeader -> WorkHeaderRow(
                title = row.title,
                detail = row.detail,
                meta = "${stringResource(row.count)} · ${formatDuration(row.durationMs)}",
                credits = row.credits,
                collapsed = if (row.collapsible) row.collapsed else null,
                playing = row.playing,
                onPlay = { onPlayWork(row.songs) },
                onClick = {
                    when (row.tap) {
                        WorkTap.ToggleCollapse -> onToggleWork(row.songs.first().id)
                        WorkTap.Play -> onPlayWork(row.songs)
                    }
                },
                onLongClick = { onWorkMore(row.title, row.songs) },
                otherRecordings = if (row.otherRecordings > 0) pluralString(R.plurals.album_detail_other_recordings, row.otherRecordings) else null,
                onOtherRecordings = { onOtherRecordings(row.songs.first().id) },
            )

            is AlbumRow.Movement -> MovementRow(
                numeral = row.numeral,
                name = row.name,
                duration = formatDuration(row.song.duration.toLong()),
                playing = row.playing,
                onClick = { onPlay(songs, row.playIndex) },
                onLongClick = { onSongMore(row.song) },
                onMore = { onSongMore(row.song) },
            )

            is AlbumRow.Track -> SongRow(
                title = row.title ?: stringResource(com.simplecityapps.core.R.string.unknown),
                subtitle = row.song.artistUnlessAlbumArtist(album),
                onClick = { onPlay(songs, row.playIndex) },
                trackNumber = row.song.track,
                duration = formatDuration(row.song.duration.toLong()),
                playing = row.playing,
                offlineState = row.song.offlineState(),
                onMore = { onSongMore(row.song) },
                tabularDuration = true,
            )
        }
    }
}
