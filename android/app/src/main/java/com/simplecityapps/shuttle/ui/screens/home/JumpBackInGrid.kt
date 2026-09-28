package com.simplecityapps.shuttle.ui.screens.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Song

/**
 * Jump back in (#633): the last things played as a compact grid of cells rather than a shelf, after Apple Music's and
 * Spotify's recents and the iOS app's `JumpBackInGrid`. [columns] wide ([jumpBackInColumns]) and at most two rows'
 * worth of [JUMP_BACK_IN_MAXIMUM_ITEMS]. A cell opens its item; its trailing button plays it.
 */
@Composable
fun JumpBackInGrid(
    items: List<HomeItem>,
    covers: Map<String, List<Song>>,
    columns: Int,
    largeText: Boolean,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
        items.take(JUMP_BACK_IN_MAXIMUM_ITEMS).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                row.forEach { item ->
                    JumpBackInCell(item, covers[item.key].orEmpty(), largeText, callbacks, Modifier.weight(1f))
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
 * One cell, after Spotify's recents: the cover flush with the cell's leading edge, the title over two lines and the
 * kind of item, on a tonal container, with a play button at the end (shuffle, for a genre). Every cell in a row is
 * the same height: the title always reserves its two lines, except at the largest font sizes (one column), where it
 * takes what it needs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JumpBackInCell(
    item: HomeItem,
    covers: List<Song>,
    largeText: Boolean,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val actions = homeItemActions(item, callbacks)
    val title = item.title()
    val shuffles = item is HomeItem.GenreItem
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ArtworkSize.Medium.dp)
                    .combinedClickable(onClick = { callbacks.onOpenItem(item) }, onLongClick = actions.showMenu)
                    .semantics { customActions = actions.accessibilityActions }
                    .testTag(JUMP_BACK_IN_CELL_TAG),
                verticalAlignment = Alignment.Top,
            ) {
                HomeItemArtwork(item, covers, ArtworkSize.Medium)
                Column(
                    modifier = Modifier.weight(1f).padding(start = S2Spacing.small, top = S2Spacing.xsmall, bottom = S2Spacing.xsmall),
                    verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        minLines = if (largeText) 1 else 2,
                        maxLines = if (largeText) LARGE_TEXT_TILE_LINES * 2 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(item.kind.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            S2IconButton(
                icon = if (shuffles) Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (shuffles) R.string.home_shuffle_item else R.string.home_play_item, title),
                onClick = { callbacks.onAction(item.playAction()) },
                modifier = Modifier.testTag(JUMP_BACK_IN_PLAY_TAG),
            )
        }
    }
}

const val JUMP_BACK_IN_CELL_TAG = "homeGrid.cell"
const val JUMP_BACK_IN_PLAY_TAG = "homeGrid.play"
