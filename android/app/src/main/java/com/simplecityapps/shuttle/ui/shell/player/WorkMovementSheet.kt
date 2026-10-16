package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.designsystem.component.MovementRow
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.NowPlayingWork

/** Every movement of [work] under its composer and title; a tap on a row calls [onSelect] with its index in the work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkMovementSheet(
    work: NowPlayingWork,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small)) {
            work.composer?.let { S2Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            S2Text(work.work, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn {
            itemsIndexed(work.tracks) { index, track ->
                MovementRow(
                    numeral = track.numeral,
                    name = track.name,
                    duration = formatDuration(track.durationMs),
                    playing = index == currentIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}
