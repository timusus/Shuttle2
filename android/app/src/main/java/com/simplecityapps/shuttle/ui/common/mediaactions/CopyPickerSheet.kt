package com.simplecityapps.shuttle.ui.common.mediaactions

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionsSheet
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.songinfo.SongCopyRow
import com.simplecityapps.shuttle.ui.screens.songinfo.SongCopyState
import com.simplecityapps.shuttle.ui.screens.sources.ProviderMark
import com.simplecityapps.shuttle.ui.screens.sources.titleRes

/** "Play from…": the copies that can play now, the one playing ticked. Choosing one plays it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyPickerSheet(
    rows: List<SongCopyRow>,
    onPick: (Song) -> Unit,
    onDismissRequest: () -> Unit,
) {
    S2ActionsSheet(
        title = stringResource(R.string.menu_title_play_from),
        actions = rows.filter { it.playable }.map { row ->
            S2Action(
                label = stringResource(row.provider.titleRes),
                onClick = { onPick(row.song) },
                selected = row.state == SongCopyState.Playing,
                supporting = listOfNotNull(row.format, row.bitRate).joinToString(" · ").ifEmpty { null },
                leading = { ProviderMark(row.provider) },
            )
        },
        onDismissRequest = onDismissRequest,
    )
}
