package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ActionsSheetContent
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ModalSheet
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.OtherRecording

/** The other albums holding a recording of the work [title], one row each; tapping one runs [onOpen] with its album's key. */
@Composable
fun OtherRecordingsSheet(
    title: String,
    recordings: List<OtherRecording>,
    onOpen: (AlbumGroupKey) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    S2ModalSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        OtherRecordingsContent(title, recordings, onOpen, onDismissRequest)
    }
}

@Composable
internal fun ColumnScope.OtherRecordingsContent(
    title: String,
    recordings: List<OtherRecording>,
    onOpen: (AlbumGroupKey) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    ActionsSheetContent(
        title = title,
        subtitle = pluralString(R.plurals.album_detail_other_recordings, recordings.size),
        actions = recordings.map { recording ->
            S2Action(
                label = recording.album ?: unknown,
                onClick = { onOpen(recording.groupKey) },
                supporting = creditLine(recording),
                leading = { Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Small, model = recording.songs.first()) },
            )
        },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun creditLine(recording: OtherRecording): String? {
    val performer = recording.leadPerformer
    val year = recording.year?.toString()
    return when {
        performer != null && year != null -> stringResource(R.string.album_detail_other_recording_credit, performer, year)
        else -> performer ?: year
    }
}
