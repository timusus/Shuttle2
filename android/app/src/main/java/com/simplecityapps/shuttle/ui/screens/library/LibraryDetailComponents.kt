package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.DetailBleedHero
import com.simplecityapps.shuttle.designsystem.component.DetailHero
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2ButtonGroup
import com.simplecityapps.shuttle.designsystem.component.S2GroupAction
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.previewArtwork
import com.simplecityapps.shuttle.designsystem.theme.S2ShelfTileWidth
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold

// The pieces every library detail screen shares, on DetailScaffold (app-shell.md, section 3): the DetailHero with
// Play / Shuffle, and the loading and not-found states.

/** What a detail screen can show: its content, a spinner while the first load runs, or "not in your library". */
enum class DetailContentState { Loading, NotFound, Ready }

/**
 * A library detail screen: [DetailScaffold] with a [DetailHero] (the artwork, title and Play / Shuffle, then [header]),
 * or with [bleed] a [DetailBleedHero] showing [artwork] full-bleed (an artist page's, #781), and an overflow that opens the item's actions sheet, after any screen-specific [actions]. [content] follows the hero;
 * [overlay] draws over it, under the bar, given the bar's height.
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
    bleed: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable () -> Unit = {},
    overlay: (@Composable BoxScope.(topInset: Dp) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    when (state) {
        DetailContentState.Loading -> DetailScaffold(title = title, subtitle = null, onNavigateUp = onNavigateUp, modifier = modifier) {
            item { LoadingState(modifier = Modifier.fillMaxWidth().padding(vertical = S2Spacing.xlarge)) }
        }

        DetailContentState.NotFound -> DetailScaffold(title = title, subtitle = null, onNavigateUp = onNavigateUp, modifier = modifier) {
            item { EmptyState(title = stringResource(R.string.library_item_not_found), modifier = Modifier.fillMaxWidth().padding(vertical = S2Spacing.xlarge)) }
        }

        DetailContentState.Ready -> DetailScaffold(
            title = title,
            subtitle = subtitle,
            onNavigateUp = onNavigateUp,
            modifier = modifier,
            listState = listState,
            heroBleeds = bleed,
            hero = { topInset ->
                val playShuffle: @Composable () -> Unit = {
                    S2ButtonGroup(
                        primary = S2GroupAction(stringResource(R.string.menu_title_play), onPlay, Icons.Rounded.PlayArrow),
                        secondary = listOf(S2GroupAction(stringResource(R.string.menu_title_shuffle), onShuffle, Icons.Rounded.Shuffle)),
                    )
                }
                if (bleed) {
                    DetailBleedHero(title = title, subtitle = subtitle, image = { BleedArtwork(artwork) }, actions = playShuffle, extra = header)
                } else {
                    DetailHero(
                        title = title,
                        subtitle = subtitle,
                        topInset = topInset,
                        artwork = { LibraryArtwork(model = artwork, placeholder = placeholder, size = ArtworkSize.Hero) },
                        actions = playShuffle,
                        extra = header,
                    )
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
            overlay = overlay,
            content = content,
        )
    }
}

/** [model]'s image filling a [DetailBleedHero]: nothing while it loads or when there's none, so the hero's own fill shows. */
@Composable
private fun BleedArtwork(model: Any?) {
    val preview = previewArtwork(model)
    when {
        preview != null -> Image(preview, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        model != null -> AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/**
 * A titled row of [albums] as grid tiles: a tap goes to [onClick], a long press to [onLongClick]. [key] names the
 * section, keying its items apart from the page's others, and is the row's test tag. Each tile's [subtitle] is whose
 * album it is unless the caller says otherwise (an artist's own albums show their year).
 */
fun LazyListScope.albumShelf(
    key: String,
    title: String,
    albums: List<Album>,
    unknown: String,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
    // Whose album it is: the album artist, not the track artists friendlyArtistName joins
    subtitle: (Album) -> String? = { it.albumArtist ?: it.friendlyArtistName },
) {
    item(key = "$key-header", contentType = "header") { SectionHeader(title = title) }
    item(key = key, contentType = "album-shelf") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = S2Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium),
            modifier = Modifier.testTag(key),
        ) {
            items(albums, key = { "$key-${it.groupKey}" }) { album ->
                GridTile(
                    title = album.name ?: unknown,
                    subtitle = subtitle(album),
                    onClick = { onClick(album) },
                    onLongClick = { onLongClick(album) },
                    artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.fillMaxSize(), size = ArtworkSize.Grid) },
                    modifier = Modifier.width(S2ShelfTileWidth.compact),
                )
            }
        }
    }
}
