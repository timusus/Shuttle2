package com.simplecityapps.shuttle.ui.screens.songinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.InfoSetting
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2InfoChip
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.PreferredCopy
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.sources.ProviderMark
import com.simplecityapps.shuttle.ui.screens.sources.titleRes

/** What a copy's overflow menu can do; the screen only reports which copy it was asked of. */
data class SongCopyActions(
    val onPlay: (Song) -> Unit,
    val onMakePrimary: (Song) -> Unit,
    val onMakePrimaryForAlbum: (Song) -> Unit,
    val onUseAutomatic: () -> Unit,
)

private val UnavailableAlpha = 0.5f

/** Every copy of the song, best first, over a footnote saying why the top one plays. */
@Composable
fun SongCopiesSection(
    copies: SongCopyRows,
    actions: SongCopyActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
        SettingsGroup(
            title = stringResource(R.string.song_info_section_copies),
            rows = copies.rows.map { row -> { shapes: ListItemShapes -> CopyRow(row, actions, shapes) } },
        )
        S2Text(
            stringResource(copies.chosenBy.footnoteRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = S2Spacing.medium),
        )
    }
}

@Composable
private fun CopyRow(
    row: SongCopyRow,
    actions: SongCopyActions,
    shapes: ListItemShapes,
) {
    val detail = listOfNotNull(row.format, row.bitRate, row.bitDepth, row.sampleRate).joinToString(" · ")
    InfoSetting(
        title = stringResource(row.provider.titleRes),
        summary = detail,
        shapes = shapes,
        modifier = if (row.state.greyed) Modifier.alpha(UnavailableAlpha) else Modifier,
        leading = { ProviderMark(row.provider) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(S2Spacing.xsmall)) {
                row.state.labelRes?.let { S2InfoChip(stringResource(it)) }
                CopyMenu(row, actions)
            }
        },
    )
}

@Composable
private fun CopyMenu(
    row: SongCopyRow,
    actions: SongCopyActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val menu = listOfNotNull(
        S2Action(stringResource(R.string.song_info_copy_play), { actions.onPlay(row.song) }).takeIf { row.playable },
        if (row.primary) {
            S2Action(stringResource(R.string.song_info_copy_use_automatic), actions.onUseAutomatic)
        } else {
            S2Action(stringResource(R.string.song_info_copy_make_primary), { actions.onMakePrimary(row.song) })
        },
        S2Action(stringResource(R.string.song_info_copy_make_album_primary), { actions.onMakePrimaryForAlbum(row.song) }),
    )
    Box {
        S2IconButton(Icons.Rounded.MoreVert, stringResource(R.string.song_info_copy_actions), onClick = { expanded = true })
        S2Menu(expanded = expanded, onDismissRequest = { expanded = false }, groups = listOf(menu))
    }
}

private val SongCopyState.greyed: Boolean
    get() = this == SongCopyState.Unreachable || this == SongCopyState.Missing || this == SongCopyState.Offline

private val SongCopyState.labelRes: Int?
    get() = when (this) {
        SongCopyState.Playing -> R.string.song_info_copy_playing
        SongCopyState.Primary -> R.string.song_info_copy_primary
        SongCopyState.Downloaded -> R.string.song_info_copy_downloaded
        SongCopyState.Unreachable -> R.string.song_info_copy_unreachable
        SongCopyState.Missing -> R.string.song_info_copy_missing
        SongCopyState.Offline -> R.string.song_info_copy_offline
        SongCopyState.Available -> null
    }

private val CopyChoice.footnoteRes: Int
    get() = when (this) {
        CopyChoice.Primary -> R.string.song_info_copies_chosen_primary

        CopyChoice.AlbumPrimary -> R.string.song_info_copies_chosen_album

        is CopyChoice.Automatic -> when (preferred) {
            PreferredCopy.Local -> R.string.song_info_copies_chosen_local
            PreferredCopy.BestQuality -> R.string.song_info_copies_chosen_quality
        }
    }
