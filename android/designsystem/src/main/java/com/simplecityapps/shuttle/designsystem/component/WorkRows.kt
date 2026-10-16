package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.rowMeta
import com.simplecityapps.shuttle.designsystem.theme.rowSubtitle
import com.simplecityapps.shuttle.designsystem.theme.rowTitle

/** Width of the movement numeral column, so numerals of any length end on one edge and the names below share another. */
private val NumeralColumn = 32.dp

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

/** Where a work's vertical rule sits, in from the screen edge; the movements indent past it. */
private val RuleInset = 24.dp
private val RuleWidth = 2.dp

/** The rule along a work's leading edge, drawn by each row of the block so it runs unbroken; [accent] marks the playing row. */
@Composable
private fun Modifier.workRule(accent: Boolean): Modifier {
    val color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        drawRect(color, topLeft = Offset(RuleInset.toPx(), 0f), size = Size(RuleWidth.toPx(), size.height))
    }
}

/**
 * One movement under a [WorkHeaderRow], indented along the work's rule: a [numeral] right-aligned in a fixed, tabular
 * column (blank for a movement that names itself), the [name], its [duration], a faint [trackNumber] and the ⋮ for
 * [onMore]. [playing] draws the playing mark beside the duration and the rule in the accent colour.
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
    onMore: (() -> Unit)? = null,
) {
    val tabular = MaterialTheme.typography.rowMeta.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .workRule(accent = playing)
            .padding(start = RuleInset + RuleWidth + S2Spacing.xsmall, end = S2Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = numeral.orEmpty(),
            modifier = Modifier.width(NumeralColumn),
            style = tabular,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            text = name,
            modifier = Modifier.weight(1f).padding(start = S2Spacing.smallMedium),
            style = MaterialTheme.typography.rowTitle,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (playing) {
            Icon(Icons.Rounded.GraphicEq, stringResource(R.string.ds_now_playing), Modifier.padding(start = S2Spacing.small).size(S2IconSize.small), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            duration,
            Modifier.padding(start = S2Spacing.small),
            style = tabular,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trackNumber != null) {
            Text(
                text = trackNumber.toString(),
                modifier = Modifier.width(28.dp).padding(start = S2Spacing.smallMedium),
                style = tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        if (onMore != null) RowMoreButton(onClick = onMore)
    }
}

/** A disc change in the middle of a work: a quiet small-caps [label] and a hairline, indented with the work's movements and its rule. */
@Composable
fun WorkDiscDivider(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .workRule(accent = false)
            .padding(start = RuleInset + RuleWidth + S2Spacing.xsmall + NumeralColumn + S2Spacing.smallMedium, end = S2Spacing.medium)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
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
            WorkDiscDivider("Disc 2")
            MovementRow(numeral = "III", name = "Finale", duration = "6:38", trackNumber = 3, onClick = {})
        }
    }
}
