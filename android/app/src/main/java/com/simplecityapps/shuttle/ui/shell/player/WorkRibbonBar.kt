package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2WorkRibbon
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.NowPlayingWork
import com.simplecityapps.shuttle.model.formatWorkRemaining
import com.simplecityapps.shuttle.model.segmentFill

/**
 * The ribbon over the current work, above the seek bar. Reads the ticking position here: the bar fill in the draw phase,
 * the caption once a second. A tap on a segment jumps to its first movement; one too thin to aim at opens the queue,
 * which lists the work's movements (there is no separate movement list).
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
    val second by remember(work) { derivedStateOf { progress().positionMs / 1000 } }
    val caption = stringResource(R.string.player_work_caption, work.ordinal, work.total, formatWorkRemaining(ribbon.remainingMs - second * 1000))
    S2WorkRibbon(
        segments = ribbon.segments,
        currentSegment = ribbon.currentSegment,
        currentFill = { ribbon.segmentFill(progress().positionMs) },
        caption = caption,
        onSegmentClick = { segment -> player.items.getOrNull(ribbon.segmentStarts[segment])?.let { actions.skipToQueueItem(it.uid) } },
        onOpenList = { actions.showPanel(NowPlayingPanel.Queue) },
        continuous = ribbon.continuous,
        modifier = modifier.padding(horizontal = S2Spacing.medium + S2Spacing.xsmall),
    )
}
