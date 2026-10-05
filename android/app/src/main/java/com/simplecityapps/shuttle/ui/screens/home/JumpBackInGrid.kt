package com.simplecityapps.shuttle.ui.screens.home

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.tileSubtitle
import com.simplecityapps.shuttle.designsystem.theme.tileTitle
import com.simplecityapps.shuttle.model.Song
import kotlin.math.roundToInt

/**
 * Jump back in (#633, #706): the most recent item as a wide resume card with the one Play button, which carries on
 * where its queue was left (#670), then the rest as a compact grid of tiles after Apple Music's and Spotify's recents
 * and the iOS app's `JumpBackInGrid`: [columns] wide ([jumpBackInColumns]), at most [JUMP_BACK_IN_MAXIMUM_TILES] of
 * them. Every card and tile opens its item; long-press offers the rest, Play from start among them.
 */
@Composable
fun JumpBackInGrid(
    items: List<HomeItem>,
    progress: Map<String, HomeItemProgress>,
    covers: Map<String, List<Song>>,
    columns: Int,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val first = items.firstOrNull() ?: return
    // Not lazy, so no animateItem: each card and tile is keyed by its item so it keeps its state as the grid changes, and
    // the grid animates its height when a row comes or goes (#672).
    Column(modifier = modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
        key(first.key) {
            JumpBackInResumeCard(first, progress[first.key], covers[first.key].orEmpty(), callbacks)
        }
        items.drop(1).take(JUMP_BACK_IN_MAXIMUM_TILES).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                row.forEach { item ->
                    key(item.key) {
                        JumpBackInTile(item, progress[item.key], covers[item.key].orEmpty(), callbacks, Modifier.weight(1f))
                    }
                }
                // A short last row keeps its tiles the width of the rows above.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The tiles under the resume card: three rows on a phone, two on a wide window's four columns (6 = 3 x 2 = 4 + 2). */
const val JUMP_BACK_IN_MAXIMUM_TILES = 6

/** Two columns on a compact window, four from medium width; one at the largest font sizes, where two would cut titles to a word. */
fun jumpBackInColumns(
    widthAtLeastMedium: Boolean,
    largeText: Boolean,
): Int = when {
    largeText -> 1
    widthAtLeastMedium -> 4
    else -> 2
}

/** When a queue was left, as "2 hours ago" or "Yesterday". */
internal fun jumpBackInRelativeTime(
    updatedAtMillis: Long,
    nowMillis: Long,
): String = DateUtils.getRelativeTimeSpanString(minOf(updatedAtMillis, nowMillis), nowMillis, DateUtils.MINUTE_IN_MILLIS).toString()

/** Whether the progress bar shows: the queue is under way, in order. A shuffled queue shows its glyph instead. */
internal fun HomeItemProgress?.showsBar(): Boolean = this != null && !finished && !shuffled && fraction > 0f

/**
 * What TalkBack reads for a card or tile: the title, the kind and where the queue was left ("OK Computer, Album, on
 * Airbag, 40% through"), and on the card when.
 */
@Composable
private fun jumpBackInDescription(
    title: String,
    kind: String,
    progress: HomeItemProgress?,
    relativeTime: String?,
): String {
    val parts = mutableListOf(title, kind)
    if (progress != null) {
        if (progress.finished) {
            parts += stringResource(R.string.home_cd_finished)
        } else {
            progress.songName?.let { parts += stringResource(R.string.home_cd_on_song, it) }
            parts += if (progress.shuffled) stringResource(R.string.home_cd_shuffled) else stringResource(R.string.home_cd_percent_through, (progress.fraction * 100).roundToInt())
        }
        relativeTime?.let { parts += it }
    }
    return parts.joinToString(", ")
}

/**
 * The most recent item: its artwork with how far through it the queue was left as a thin bar, the kind and when
 * ("Album · Yesterday"), the title, and the song it was left on (a shuffle glyph before it when shuffled, "Finished ·
 * Play again" when played through), with the one Play button (Shuffle, for a genre never played) that resumes
 * ([HomeItem.resumeAction]). A tap anywhere else opens the item; long-press offers Play from start and the rest.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JumpBackInResumeCard(
    item: HomeItem,
    progress: HomeItemProgress?,
    covers: List<Song>,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val actions = homeItemActions(item, callbacks, resumes = true)
    val title = item.title()
    val kind = stringResource(item.kind.label)
    val shuffles = item is HomeItem.GenreItem && (progress == null || progress.finished)
    val now = remember { System.currentTimeMillis() }
    val relativeTime = progress?.let { jumpBackInRelativeTime(it.updatedAt.toEpochMilliseconds(), now) }
    val overline = relativeTime?.let { stringResource(R.string.home_tile_subtitle, kind, it) } ?: kind
    val playLabel = stringResource(
        when {
            progress != null && !progress.finished -> R.string.home_resume_item
            shuffles -> R.string.home_shuffle_item
            else -> R.string.home_play_item
        },
        title,
    )
    JumpBackInSurface(MaterialTheme.shapes.large, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = { callbacks.onOpenItem(item) }, onLongClick = actions.showMenu)
                    .semantics {
                        contentDescription = jumpBackInDescription(title, kind, progress, relativeTime)
                        customActions = actions.accessibilityActions
                    }
                    .testTag(JUMP_BACK_IN_CARD_TAG)
                    .padding(S2Spacing.small),
                horizontalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JumpBackInArtwork(item, covers, ArtworkSize.Feature, progress)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny, Alignment.CenterVertically)) {
                    S2Text(overline, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    S2Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    JumpBackInSongLine(progress)
                }
            }
            S2IconButton(
                icon = if (shuffles) Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow,
                contentDescription = playLabel,
                onClick = { callbacks.onAction(item.resumeAction()) },
                modifier = Modifier.padding(end = S2Spacing.small).testTag(JUMP_BACK_IN_PLAY_TAG),
                style = S2IconButtonStyle.Filled,
                size = S2IconButtonSize.Medium,
            )
        }
    }
}

/** The card's third line: the song the queue was left on, with a shuffle glyph before it when shuffled; "Finished · Play again" when played through. */
@Composable
private fun JumpBackInSongLine(progress: HomeItemProgress?) {
    val text = when {
        progress == null -> null
        progress.finished -> stringResource(R.string.home_finished_play_again)
        else -> progress.songName ?: stringResource(R.string.home_state_shuffled).takeIf { progress.shuffled }
    } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(S2Spacing.xsmall)) {
        if (progress?.shuffled == true && !progress.finished) {
            Image(
                Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(S2IconSize.small).testTag(JUMP_BACK_IN_SHUFFLED_TAG),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        S2Text(text, style = MaterialTheme.typography.tileSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One compact tile, after Spotify's recents: the cover flush with the tile's leading edge, with how far through the item
 * its queue was left as a thin bar along its foot, and beside it the title over up to two lines and the kind then where
 * it was left ("Album · Airbag", "Playlist · Shuffled", "Genre · Finished"). It has no play button: a tap opens the
 * item, long-press offers Play (resuming), Play from start and the rest. TalkBack reads the kind, the song and the state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JumpBackInTile(
    item: HomeItem,
    progress: HomeItemProgress?,
    covers: List<Song>,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val actions = homeItemActions(item, callbacks, resumes = true)
    val title = item.title()
    val kind = stringResource(item.kind.label)
    val state = when {
        progress == null -> null
        progress.finished -> stringResource(R.string.home_state_finished)
        progress.shuffled -> stringResource(R.string.home_state_shuffled)
        else -> progress.songName
    }
    val detail = state?.let { stringResource(R.string.home_tile_subtitle, kind, it) } ?: kind
    JumpBackInSurface(MaterialTheme.shapes.medium, modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ArtworkSize.Medium.dp)
                .combinedClickable(onClick = { callbacks.onOpenItem(item) }, onLongClick = actions.showMenu)
                .semantics {
                    contentDescription = jumpBackInDescription(title, kind, progress, relativeTime = null)
                    customActions = actions.accessibilityActions
                }
                .testTag(JUMP_BACK_IN_CELL_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JumpBackInArtwork(item, covers, ArtworkSize.Medium, progress)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = S2Spacing.small, vertical = S2Spacing.xsmall),
                verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny, Alignment.CenterVertically),
            ) {
                S2Text(title, style = MaterialTheme.typography.tileTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                S2Text(detail, style = MaterialTheme.typography.tileSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The tonal container a card or tile sits on. */
@Composable
private fun JumpBackInSurface(
    shape: Shape,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, content = content)
}

/** The item's artwork, with the progress bar along its foot while the queue is under way. */
@Composable
private fun JumpBackInArtwork(
    item: HomeItem,
    covers: List<Song>,
    size: ArtworkSize,
    progress: HomeItemProgress?,
) {
    Box(modifier = Modifier.size(size.dp)) {
        HomeItemArtwork(item, covers, size)
        if (progress != null && progress.showsBar()) {
            JumpBackInProgressBar(progress.fraction, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** How far through an item its queue was left: a thin bar along the foot of its artwork, on a scrim. */
@Composable
private fun JumpBackInProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(S2Spacing.tiny).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))) {
        Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

const val JUMP_BACK_IN_CARD_TAG = "homeGrid.card"
const val JUMP_BACK_IN_CELL_TAG = "homeGrid.cell"
const val JUMP_BACK_IN_PLAY_TAG = "homeGrid.play"
const val JUMP_BACK_IN_SHUFFLED_TAG = "homeGrid.shuffled"
