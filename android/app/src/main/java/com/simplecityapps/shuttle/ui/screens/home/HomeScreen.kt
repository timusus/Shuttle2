package com.simplecityapps.shuttle.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork

class HomeCallbacks(
    val onSearch: () -> Unit = {},
    val onShuffleAll: () -> Unit = {},
    val onOpenWhatsNew: () -> Unit = {},
    val onDismissWhatsNew: () -> Unit = {},
    val onAlbumClick: (Album) -> Unit = {},
    val onArtistClick: (AlbumArtist) -> Unit = {},
    val onShowActions: (MediaActionsTarget) -> Unit = {},
)

/** Home: the library's shelves under a collapsing top bar, or the empty state when there's no music yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            S2LargeTopBar(
                title = stringResource(R.string.home_title),
                actions = { S2IconButton(icon = Icons.Rounded.Search, contentDescription = stringResource(R.string.home_search), onClick = callbacks.onSearch) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (uiState) {
            HomeUiState.Loading -> LoadingState(contentModifier)

            HomeUiState.Empty -> EmptyState(
                title = stringResource(R.string.home_empty_title),
                message = stringResource(R.string.home_empty_message),
                modifier = contentModifier,
            )

            is HomeUiState.Content -> HomeContent(uiState, callbacks, contentModifier)
        }
    }
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    callbacks: HomeCallbacks,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "shuffle") {
            S2Button(
                text = stringResource(R.string.home_shuffle_all),
                onClick = callbacks.onShuffleAll,
                icon = Icons.Rounded.Shuffle,
                style = S2ButtonStyle.Tonal,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (content.showWhatsNew) {
            item(key = "whats-new") { WhatsNewCard(callbacks) }
        }
        albumCarousel(R.string.home_recently_played, "recently-played", content.recentlyPlayed, callbacks)
        albumCarousel(R.string.home_recently_added, "recently-added", content.recentlyAdded, callbacks)
        shelf(R.string.home_most_played, "most-played", content.mostPlayed) { album -> AlbumTile(album, callbacks, showPlayCount = true) }
        shelf(R.string.home_something_different, "something-different", content.somethingDifferent) { artist -> ArtistTile(artist, callbacks) }
    }
}

@Composable
private fun WhatsNewCard(callbacks: HomeCallbacks) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.home_whats_new_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            S2IconButton(icon = Icons.Rounded.Close, contentDescription = stringResource(R.string.home_whats_new_dismiss), onClick = callbacks.onDismissWhatsNew)
        }
        Text(
            text = stringResource(R.string.home_whats_new_message).replace("{version}", BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        S2Button(
            text = stringResource(R.string.home_whats_new_open),
            onClick = callbacks.onOpenWhatsNew,
            style = S2ButtonStyle.Text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

/** A multi-browse carousel of albums (inventory §3), hidden when there are none. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private fun LazyListScope.albumCarousel(
    @StringRes title: Int,
    key: String,
    albums: List<Album>,
    callbacks: HomeCallbacks,
) {
    if (albums.isEmpty()) return
    item(key = "$key:header") { SectionHeader(title = stringResource(title)) }
    item(key = key) {
        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { albums.size },
            preferredItemWidth = 200.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(200.dp),
        ) { index ->
            val album = albums[index]
            val title = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown)
            val artist = album.albumArtist ?: album.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .combinedClickable(
                        onClick = { callbacks.onAlbumClick(album) },
                        onLongClick = { callbacks.onShowActions(MediaActionsTarget(title, artist, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
                    ),
            ) {
                LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.fillMaxSize(), size = ArtworkSize.Hero)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        // Keep the label at the visible edge and fade it out as the item shrinks toward small.
                        .graphicsLayer {
                            val info = carouselItemDrawInfo
                            translationX = info.maskRect.left
                            alpha = if (info.maxSize > info.minSize) ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f).let { it * it } else 1f
                        }
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                        .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A horizontally scrolling row of [GridTile]s, hidden when there are none. */
private fun <T> LazyListScope.shelf(
    @StringRes title: Int,
    key: String,
    items: List<T>,
    tile: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "$key:header") { SectionHeader(title = stringResource(title)) }
    item(key = key) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(items) { _, item -> Box(Modifier.width(ShelfTileWidth)) { tile(item) } }
        }
    }
}

@Composable
private fun AlbumTile(
    album: Album,
    callbacks: HomeCallbacks,
    showPlayCount: Boolean,
) {
    val title = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown)
    val artist = album.albumArtist ?: album.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
    GridTile(
        title = title,
        subtitle = artist,
        onClick = { callbacks.onAlbumClick(album) },
        onLongClick = { callbacks.onShowActions(MediaActionsTarget(title, artist, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
        artwork = {
            Box {
                LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.fillMaxSize(), size = ArtworkSize.Grid)
                if (showPlayCount) PlayCountBadge(album.playCount, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        },
    )
}

@Composable
private fun ArtistTile(
    artist: AlbumArtist,
    callbacks: HomeCallbacks,
) {
    val name = artist.name ?: artist.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
    val albums = pluralStringResource(R.plurals.albumsPlural, artist.albumCount, artist.albumCount).replace("{count}", artist.albumCount.toString())
    GridTile(
        title = name,
        subtitle = albums,
        onClick = { callbacks.onArtistClick(artist) },
        onLongClick = { callbacks.onShowActions(MediaActionsTarget(name, null, MediaSelection.AlbumArtists(artist), ArtworkPlaceholder.Artist)) },
        artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, Modifier.fillMaxSize(), size = ArtworkSize.Grid, shape = ArtworkShape.Circle) },
    )
}

@Composable
private fun PlayCountBadge(
    playCount: Int,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(R.plurals.home_play_count, playCount, playCount).replace("{count}", playCount.toString())
    Text(
        text = playCount.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = description },
    )
}

private val ShelfTileWidth = 148.dp
