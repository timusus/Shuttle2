package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2WorkRibbon
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.NowPlayingWork
import com.simplecityapps.shuttle.model.formatWorkRemaining
import com.simplecityapps.shuttle.model.segmentFill

/**
 * The ribbon over the current work, above the seek bar. Reads the ticking position here: the bar fill in the draw phase,
 * the caption once a second. A tap on a segment jumps to its first movement; one too thin to aim at, or the caption,
 * opens the movement list.
 */
@Composable
internal fun WorkRibbonBar(
    work: NowPlayingWork,
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val ribbon = work.ribbon
    val resources = LocalResources.current
    var showMovements by remember { mutableStateOf(false) }
    val second by remember(work) { derivedStateOf { progress().positionMs / 1000 } }
    val caption = stringResource(R.string.player_work_caption, work.ordinal, work.total, formatWorkRemaining(ribbon.remainingMs - second * 1000))
    S2WorkRibbon(
        segments = ribbon.segments,
        currentSegment = ribbon.currentSegment,
        currentFill = { ribbon.segmentFill(progress().positionMs) },
        caption = caption,
        onSegmentClick = { segment -> player.items.getOrNull(ribbon.segmentStarts[segment])?.let { actions.skipToQueueItem(it.uid) } },
        onOpenList = { showMovements = true },
        segmentLabel = { segment -> resources.getString(R.string.player_work_play_movement, work.ordinals.getOrElse(ribbon.segmentStarts[segment] - ribbon.segmentStarts.first()) { (segment + 1).toString() }) },
        openListLabel = stringResource(R.string.player_work_show_movements),
        continuous = ribbon.continuous,
        modifier = modifier.padding(horizontal = S2Spacing.medium + S2Spacing.xsmall),
    )
    if (showMovements) {
        val first = ribbon.segmentStarts.first()
        WorkMovementSheet(
            work = work,
            currentIndex = player.items.indexOfFirst { it.position == QueuePosition.Current } - first,
            onSelect = { index ->
                player.items.getOrNull(first + index)?.let { actions.skipToQueueItem(it.uid) }
                showMovements = false
            },
            onDismiss = { showMovements = false },
        )
    }
}
