package com.simplecityapps.shuttle.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.core.R as CoreR
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonGroup
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2GroupAction
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.theme.LocalCompactMode
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.library.nameKey
import com.simplecityapps.shuttle.ui.text.stringResource as stringResourceKey

class HomeCallbacks(
    val onShuffleAll: () -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenRecentlyAdded: () -> Unit = {},
    val onOpenFavourites: () -> Unit = {},
    val onTogglePlayback: () -> Unit = {},
    val onShuffleQueue: () -> Unit = {},
    val onOpenWhatsNew: () -> Unit = {},
    val onDismissWhatsNew: () -> Unit = {},
    val onAlbumClick: (Album) -> Unit = {},
    val onArtistClick: (AlbumArtist) -> Unit = {},
    val onPlayAlbums: (List<Album>) -> Unit = {},
    val onPlayArtists: (List<AlbumArtist>) -> Unit = {},
    val onShowActions: (MediaActionsTarget) -> Unit = {},
)

/**
 * Home: the shortcut row over the library's shelves, or the empty state when there's no music yet.
 * There's no top bar: the shortcut row holds Shuffle all, and settings and search live in their
 * own tabs, so the first screen is music.
 *
 * Classic restores the old home exactly: a centered logo over "Shuttle Music Player" and dark
 * tinted shortcut circles with coloured outline glyphs. Modern keeps a small left-aligned brand
 * row and solid bright circles with white glyphs.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    /** Shown in place of the generic empty state while the library has no songs (#422), so it can offer access. */
    emptyContent: (@Composable (Modifier) -> Unit)? = null,
) {
    // No top bar: the shell pads the nav bar and player, and the list takes the status bar.
    HomeBody(uiState, callbacks, modifier.fillMaxSize().statusBarsPadding(), emptyContent)
}

@Composable
private fun HomeBody(
    uiState: HomeUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier,
    emptyContent: (@Composable (Modifier) -> Unit)?,
) {
    when (uiState) {
        HomeUiState.Loading -> LoadingState(modifier)

        HomeUiState.Empty -> if (emptyContent != null) {
            emptyContent(modifier)
        } else {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                message = stringResource(R.string.home_empty_message),
                modifier = modifier,
            )
        }

        is HomeUiState.Content -> HomeContent(uiState, callbacks, modifier)
    }
}

@Composable
private fun HomeContent(
    content: HomeUiState.Content,
    callbacks: HomeCallbacks,
    modifier: Modifier,
) {
    val compact = LocalCompactMode.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = if (compact) 8.dp else 16.dp),
    ) {
        item(key = "brand") { BrandHeader() }
        item(key = "shortcuts") { SmartShortcuts(callbacks) }
        content.resume?.let { resume ->
            item(key = "resume") { ResumeHero(resume, callbacks) }
        }
        if (content.showWhatsNew) {
            item(key = "whats-new") { WhatsNewCard(callbacks) }
        }
        shelf(R.string.home_recently_played, if (compact) R.string.home_recently_played_subtitle else null, "recently-played", content.recentlyPlayed, onPlay = { callbacks.onPlayAlbums(content.recentlyPlayed) }) { album -> AlbumTile(album, callbacks, showPlayCount = false) }
        shelf(R.string.home_recently_added, if (compact) R.string.home_recently_added_subtitle else null, "recently-added", content.recentlyAdded, onPlay = { callbacks.onPlayAlbums(content.recentlyAdded) }) { album -> AlbumTile(album, callbacks, showPlayCount = false) }
        shelf(R.string.home_most_played, if (compact) R.string.home_most_played_subtitle else null, "most-played", content.mostPlayed, onPlay = { callbacks.onPlayAlbums(content.mostPlayed) }) { album -> AlbumTile(album, callbacks, showPlayCount = compact) }
        shelf(R.string.home_something_different, if (compact) R.string.home_something_different_subtitle else null, "something-different", content.somethingDifferent, onPlay = { callbacks.onPlayArtists(content.somethingDifferent) }) { artist -> ArtistTile(artist, callbacks) }
    }
}

/**
 * The brand: classic centers a large logo over "Shuttle Music Player" like the old home; modern
 * keeps a small left-aligned logo and name row.
 */
@Composable
private fun BrandHeader() {
    if (LocalCompactMode.current) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(CoreR.drawable.ic_shuttle_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(R.string.home_brand_full),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(CoreR.drawable.ic_shuttle_logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.home_brand),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The old home's shortcut row: the three smart playlists plus Shuffle all. Classic matches the
 * reference exactly: dark tinted circles with coloured outline glyphs. Modern uses solid bright
 * circles with white filled glyphs. The playlists open their detail screens; the names match the
 * Playlists tab's rows.
 */
@Composable
private fun SmartShortcuts(callbacks: HomeCallbacks) {
    val compact = LocalCompactMode.current
    // Classic circles are smaller (48dp) with a taller top gap under the status bar; breathing
    // room under the labels separates them from the first shelf header.
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (compact) 12.dp else 16.dp, end = if (compact) 12.dp else 16.dp, top = if (compact) 12.dp else 16.dp, bottom = if (compact) 8.dp else 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (compact) {
            // The legacy glyphs, restored from the pre-rewrite resources.
            Shortcut(
                icon = painterResource(R.drawable.ic_history_black_24dp),
                label = stringResourceKey(SmartPlaylistId.History.nameKey),
                onClick = callbacks.onOpenHistory,
                containerColor = Color(0xFF1E2E28),
                glyphColor = Color(0xFF4DB6AC),
            )
            Shortcut(
                icon = painterResource(R.drawable.ic_playlist_add_black_24dp),
                label = stringResourceKey(SmartPlaylistId.RecentlyAdded.nameKey),
                onClick = callbacks.onOpenRecentlyAdded,
                containerColor = Color(0xFF2E2A18),
                glyphColor = Color(0xFFFFB300),
            )
            Shortcut(
                icon = painterResource(R.drawable.ic_favorite_border_black_24dp),
                label = stringResourceKey(SmartPlaylistId.Favourites.nameKey),
                onClick = callbacks.onOpenFavourites,
                containerColor = Color(0xFF331B1E),
                glyphColor = Color(0xFFE53935),
            )
            Shortcut(
                icon = painterResource(R.drawable.ic_shuffle_black_24dp),
                label = stringResource(R.string.btn_shuffle),
                onClick = callbacks.onShuffleAll,
                containerColor = Color(0xFF182430),
                glyphColor = Color(0xFF42A5F5),
            )
        } else {
            Shortcut(
                icon = rememberVectorPainter(Icons.Filled.History),
                label = stringResourceKey(SmartPlaylistId.History.nameKey),
                onClick = callbacks.onOpenHistory,
                containerColor = Color(0xFFEF6C00),
                glyphColor = Color.White,
            )
            Shortcut(
                icon = rememberVectorPainter(Icons.Filled.LibraryAdd),
                label = stringResourceKey(SmartPlaylistId.RecentlyAdded.nameKey),
                onClick = callbacks.onOpenRecentlyAdded,
                containerColor = Color(0xFF43A047),
                glyphColor = Color.White,
            )
            Shortcut(
                icon = rememberVectorPainter(Icons.Filled.Favorite),
                label = stringResourceKey(SmartPlaylistId.Favourites.nameKey),
                onClick = callbacks.onOpenFavourites,
                containerColor = Color(0xFFE53935),
                glyphColor = Color.White,
            )
            Shortcut(
                icon = rememberVectorPainter(Icons.Filled.Shuffle),
                label = stringResource(R.string.btn_shuffle),
                onClick = callbacks.onShuffleAll,
                containerColor = Color(0xFF1E88E5),
                glyphColor = Color.White,
            )
        }
    }
}

@Composable
private fun RowScope.Shortcut(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    glyphColor: Color,
) {
    val compact = LocalCompactMode.current
    val circle = if (compact) 48.dp else 56.dp
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick, onClickLabel = label, role = Role.Button)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(circle)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = glyphColor, modifier = Modifier.size(if (compact) 24.dp else 28.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The current queue, to pick up where it was left: its song's album art and title, the artist and time left, Play and Shuffle. */
@Composable
private fun ResumeHero(
    resume: ResumeQueue,
    callbacks: HomeCallbacks,
) {
    val compact = LocalCompactMode.current
    if (compact) {
        // Classic has no hero card: a flat row like any other list item.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("resume-hero"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResumeHeroContent(resume, callbacks)
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("resume-hero")) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ResumeHeroContent(resume, callbacks)
            }
        }
    }
}

@Composable
private fun RowScope.ResumeHeroContent(
    resume: ResumeQueue,
    callbacks: HomeCallbacks,
) {
    val song = resume.song
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val timeLeft = stringResource(R.string.home_resume_time_left, formatDuration(resume.timeLeftMs))
    val compact = LocalCompactMode.current
    // The artwork slot is a fixed box the art fills: Artwork's own size would override a bare
    // size modifier, so the compact value would never apply.
    Box(Modifier.size(ResumeArtworkSize)) {
        LibraryArtwork(song, ArtworkPlaceholder.Album, Modifier.fillMaxSize(), size = ArtworkSize.Grid)
    }
    Column(modifier = Modifier.weight(1f).padding(start = if (compact) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_resume_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(song.album ?: unknown, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOf(song.albumArtist ?: song.friendlyArtistName ?: unknown, timeLeft).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.padding(top = if (LocalCompactMode.current) 2.dp else 4.dp)) {
                    S2ButtonGroup(
                        primary = if (resume.playing) {
                            S2GroupAction(stringResource(DesignR.string.ds_pause), callbacks.onTogglePlayback, Icons.Rounded.Pause)
                        } else {
                            S2GroupAction(stringResource(R.string.menu_title_play), callbacks.onTogglePlayback, Icons.Rounded.PlayArrow)
                        },
                        secondary = listOf(S2GroupAction(stringResource(R.string.menu_title_shuffle), callbacks.onShuffleQueue, Icons.Rounded.Shuffle)),
                    )
                }
    }
}

private val ResumeArtworkSize: Dp
    @Composable get() = if (LocalCompactMode.current) 40.dp else 64.dp

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
            text = stringResource(R.string.home_whats_new_message, BuildConfig.VERSION_NAME),
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

/**
 * A plain horizontally scrolling row of [GridTile]s, hidden when there are none. The tiles are [ShelfTileWidth] wide so
 * about three fit a phone (four in compact mode) and the cut-off one says the row scrolls (#490); each title and artist sits below
 * its cover rather than over it, where they'd clash with text printed on the art (#404).
 */
private fun <T> LazyListScope.shelf(
    @StringRes title: Int,
    @StringRes subtitle: Int?,
    key: String,
    items: List<T>,
    onPlay: (() -> Unit)? = null,
    tile: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "$key:header") {
        val shelfTitle = stringResource(title)
        val compactHeader = LocalCompactMode.current
        // The top gap separates shelves; the bottom gap keeps the header off the artwork.
        SectionHeader(
            title = shelfTitle,
            subtitle = subtitle?.let { stringResource(it) },
            iconAction = onPlay?.let { Icons.Rounded.PlayArrow },
            iconActionContentDescription = onPlay?.let { stringResource(R.string.home_play_shelf, shelfTitle) },
            onIconAction = { onPlay?.invoke() },
            modifier = Modifier.padding(top = if (compactHeader) 8.dp else 12.dp, bottom = 4.dp),
        )
    }
    item(key = key) {
        val compact = LocalCompactMode.current
        LazyRow(
            contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            modifier = Modifier.padding(bottom = if (compact) 12.dp else 16.dp),
        ) {
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
        badge = if (showPlayCount) album.playCount.toString() else null,
        onClick = { callbacks.onAlbumClick(album) },
        onLongClick = { callbacks.onShowActions(MediaActionsTarget(title, artist, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
        artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.fillMaxSize(), size = ArtworkSize.Grid) },
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

private val ShelfTileWidth: Dp
    @Composable get() = if (LocalCompactMode.current) 128.dp else 144.dp
