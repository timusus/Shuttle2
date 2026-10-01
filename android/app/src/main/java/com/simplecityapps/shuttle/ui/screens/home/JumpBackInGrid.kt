package com.simplecityapps.shuttle.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Song

/**
 * Jump back in (#633): the last things played as a compact grid of cells rather than a shelf, after Apple Music's and
 * Spotify's recents and the iOS app's `JumpBackInGrid`. [columns] wide ([jumpBackInColumns]) and at most two rows'
 * worth of [JUMP_BACK_IN_MAXIMUM_ITEMS]. A cell opens its item; its trailing button plays it where [showPlayButton]
 * (there is room), otherwise long-press offers Play with the rest of the item's actions.
 */
@Composable
fun JumpBackInGrid(
    items: List<HomeItem>,
    progress: Map<String, HomeItemProgress>,
    covers: Map<String, List<Song>>,
    columns: Int,
    showPlayButton: Boolean,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    // Not lazy, so no animateItem: each cell is keyed by its item so it keeps its state as the grid changes, and the grid
    // animates its height when a row comes or goes (#672).
    Column(modifier = modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
        items.take(JUMP_BACK_IN_MAXIMUM_ITEMS).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                row.forEach { item ->
                    key(item.key) {
                        JumpBackInCell(item, progress[item.key], covers[item.key].orEmpty(), showPlayButton, callbacks, Modifier.weight(1f))
                    }
                }
                // A short last row keeps its cells the width of the rows above.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The grid holds two full rows at most, of whichever width it is: 2 x 4 on a phone, 4 x 2 wider. */
const val JUMP_BACK_IN_MAXIMUM_ITEMS = 8

/** Two columns on a compact window, four from medium width; one at the largest font sizes, where two would cut titles to a word. */
fun jumpBackInColumns(
    widthAtLeastMedium: Boolean,
    largeText: Boolean,
): Int = when {
    largeText -> 1
    widthAtLeastMedium -> 4
    else -> 2
}

/**
 * One cell, after Spotify's recents: the cover at the cell's leading edge, the title on up to two lines and the kind
 * of item as a one-line label under it, then the song its queue was left on ([progress], #670, #706), centred on a tonal
 * container of a minimum height, with a play button at the end (shuffle, for a genre) when [showPlayButton]. A compact
 * cell has none, so titles keep the room (#660). Its play, and Play in its long-press sheet, carry on where the queue
 * was left; the sheet's Play from start starts over.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JumpBackInCell(
    item: HomeItem,
    progress: HomeItemProgress?,
    covers: List<Song>,
    showPlayButton: Boolean,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val actions = homeItemActions(item, callbacks, resumes = true)
    val title = item.title()
    val shuffles = item is HomeItem.GenreItem
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = CELL_HEIGHT)
                    .combinedClickable(onClick = { callbacks.onOpenItem(item) }, onLongClick = actions.showMenu)
                    .semantics { customActions = actions.accessibilityActions }
                    .testTag(JUMP_BACK_IN_CELL_TAG),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeItemArtwork(item, covers, ArtworkSize.Medium)
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = S2Spacing.small, vertical = S2Spacing.xsmall),
                    verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny, Alignment.CenterVertically),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The kind, then the song its queue was left on, on a line of its own: a phone cell is too narrow for both.
                    listOfNotNull(stringResource(item.kind.label), progress?.takeUnless { it.finished }?.songName).forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showPlayButton) {
                S2IconButton(
                    icon = if (shuffles) Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (shuffles) R.string.home_shuffle_item else R.string.home_play_item, title),
                    onClick = { callbacks.onAction(item.resumeAction()) },
                    modifier = Modifier.testTag(JUMP_BACK_IN_PLAY_TAG),
                )
            }
        }
    }
}

/** Room for a two-line title and the label under it; the cover, 56dp, is centred in it. */
private val CELL_HEIGHT = 72.dp

const val JUMP_BACK_IN_CELL_TAG = "homeGrid.cell"
const val JUMP_BACK_IN_PLAY_TAG = "homeGrid.play"
