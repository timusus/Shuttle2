package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.rowMeta
import com.simplecityapps.shuttle.designsystem.theme.rowSubtitle
import com.simplecityapps.shuttle.designsystem.theme.rowTitle

/** Width of the movement numeral column, so numerals of any length line up and the names below share one edge. */
private val NumeralColumn = 40.dp

/**
 * A classical work on an album page: its [title], a [detail] line (key, catalogue, arrangement), an optional [credits]
 * line and a [meta] summary ("4 movements · 31:20") on the right. The play button plays the whole work; tapping the row
 * runs [onClick] (a collapse toggle, for a [collapsed] of non-null) and long-pressing it [onLongClick]. [playing] tints the
 * title while one of its movements is the current song.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkHeaderRow(
    title: String,
    detail: String?,
    meta: String,
    onPlay: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    credits: String? = null,
    collapsed: Boolean? = null,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = S2Spacing.medium, end = S2Spacing.medium, top = S2Spacing.small, bottom = S2Spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.rowTitle,
                fontWeight = FontWeight.SemiBold,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(detail, credits).forEach {
                Text(it, style = MaterialTheme.typography.rowSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(meta, style = MaterialTheme.typography.rowMeta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (collapsed != null) {
            Icon(
                imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = stringResource(if (collapsed) R.string.ds_expand_work else R.string.ds_collapse_work),
                modifier = Modifier.size(S2IconSize.small),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        S2IconButton(icon = Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.ds_play_work, title), onClick = onPlay)
    }
}

/**
 * One movement under a [WorkHeaderRow]: a [numeral] in a fixed, tabular column (blank for a movement that names itself),
 * the [name], its [duration] and a faint [trackNumber] at the trailing edge. [playing] swaps the numeral for the playing mark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovementRow(
    numeral: String?,
    name: String,
    duration: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = S2Spacing.medium, end = S2Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(NumeralColumn), contentAlignment = Alignment.CenterStart) {
            if (playing) {
                Icon(Icons.Rounded.GraphicEq, stringResource(R.string.ds_now_playing), Modifier.size(S2IconSize.small), tint = MaterialTheme.colorScheme.primary)
            } else if (numeral != null) {
                Text(numeral, style = MaterialTheme.typography.rowMeta.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.rowTitle,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(duration, Modifier.padding(start = S2Spacing.smallMedium), style = MaterialTheme.typography.rowMeta.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (trackNumber != null) {
            Text(
                text = trackNumber.toString(),
                modifier = Modifier.width(28.dp).padding(start = S2Spacing.smallMedium),
                style = MaterialTheme.typography.rowMeta.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Preview
@Composable
private fun WorkRowsPreview() {
    S2Preview {
        Column {
            WorkHeaderRow(title = "Sonata No. 3", detail = "C minor · Op. 10", meta = "3 movements · 24:10", credits = "Ines Varga", onPlay = {}, onClick = {})
            MovementRow(numeral = "I", name = "Allegro", duration = "8:02", trackNumber = 1, onClick = {})
            MovementRow(numeral = "II", name = "Adagio", duration = "9:30", trackNumber = 2, playing = true, onClick = {})
            MovementRow(numeral = "III", name = "Finale", duration = "6:38", trackNumber = 3, onClick = {})
        }
    }
}
