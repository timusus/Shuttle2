package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2ButtonGroup
import com.simplecityapps.shuttle.designsystem.component.S2GroupAction
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold

// The pieces every library detail screen shares, on DetailScaffold (app-shell.md, section 3): a centred hero,
// the title block with Play / Shuffle, and the loading and not-found states.

/** What a detail screen can show: its content, a spinner while the first load runs, or "not in your library". */
enum class DetailContentState { Loading, NotFound, Ready }

/**
 * A library detail screen: [DetailScaffold] with the hero artwork, the title block and an overflow that opens the
 * item's actions sheet, after any screen-specific [actions]. [content] follows the title block.
 */
@Composable
fun LibraryDetailScaffold(
    state: DetailContentState,
    title: String,
    subtitle: String?,
    artwork: Any?,
    placeholder: ArtworkPlaceholder,
    onNavigateUp: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    artworkShape: ArtworkShape = ArtworkShape.Rounded,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    when (state) {
        DetailContentState.Loading -> DetailScaffold(title = title, subtitle = null, onNavigateUp = onNavigateUp, modifier = modifier) {
            item { LoadingState(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)) }
        }

        DetailContentState.NotFound -> DetailScaffold(title = title, subtitle = null, onNavigateUp = onNavigateUp, modifier = modifier) {
            item { EmptyState(title = stringResource(R.string.library_item_not_found), modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)) }
        }

        DetailContentState.Ready -> DetailScaffold(
            title = title,
            subtitle = subtitle,
            onNavigateUp = onNavigateUp,
            modifier = modifier,
            listState = listState,
            hero = {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                    LibraryArtwork(model = artwork, placeholder = placeholder, size = ArtworkSize.Hero, shape = artworkShape)
                }
            },
            actions = {
                actions()
                S2IconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.library_more_options),
                    onClick = onMore,
                    modifier = Modifier.testTag("detail-more"),
                )
            },
        ) {
            item(key = "detail-header", contentType = "header") {
                DetailHeader(title = title, subtitle = subtitle, onPlay = onPlay, onShuffle = onShuffle, extra = header)
            }
            content()
        }
    }
}

/** Title, subtitle and the Play / Shuffle button group under the hero. */
@Composable
private fun DetailHeader(
    title: String,
    subtitle: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    extra: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.padding(top = 12.dp)) {
            S2ButtonGroup(
                primary = S2GroupAction(stringResource(R.string.menu_title_play), onPlay, Icons.Rounded.PlayArrow),
                secondary = listOf(S2GroupAction(stringResource(R.string.menu_title_shuffle), onShuffle, Icons.Rounded.Shuffle)),
            )
        }
        extra()
    }
}
