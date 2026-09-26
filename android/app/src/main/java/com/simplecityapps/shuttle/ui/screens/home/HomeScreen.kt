package com.simplecityapps.shuttle.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
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
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.library.pluralString

class HomeCallbacks(
    val onOpenSettings: () -> Unit = {},
    val onShuffleAll: () -> Unit = {},
    val onTogglePlayback: () -> Unit = {},
    val onShuffleQueue: () -> Unit = {},
    val onOpenWhatsNew: () -> Unit = {},
    val onDismissWhatsNew: () -> Unit = {},
    val onAlbumClick: (Album) -> Unit = {},
    val onArtistClick: (AlbumArtist) -> Unit = {},
    val onShowActions: (MediaActionsTarget) -> Unit = {},
)

/**
 * Home: a hero to resume the queue over the library's shelves, or the empty state when there's no music yet. There's
 * no page title (#490): the bar holds only Shuffle all and the Settings gear, so the first screen is music. Search is
 * its own tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    /** Shown in place of the generic empty state while the library has no songs (#422), so it can offer access. */
    emptyContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            S2TopBar(
                title = "",
                actions = {
                    if (uiState is HomeUiState.Content) {
                        S2IconButton(icon = Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.home_shuffle_all), onClick = callbacks.onShuffleAll)
                    }
                    S2IconButton(icon = Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_menu_settings), onClick = callbacks.onOpenSettings)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (uiState) {
            HomeUiState.Loading -> LoadingState(contentModifier)

            HomeUiState.Empty -> if (emptyContent != null) {
                emptyContent(contentModifier)
            } else {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    modifier = contentModifier,
                )
            }

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
        content.resume?.let { resume ->
            item(key = "resume") { ResumeHero(resume, callbacks) }
        }
        if (content.showWhatsNew) {
            item(key = "whats-new") { WhatsNewCard(callbacks) }
        }
        shelf(R.string.home_recently_played, "recently-played", content.recentlyPlayed) { album -> AlbumTile(album, callbacks, showPlayCount = false) }
        shelf(R.string.home_recently_added, "recently-added", content.recentlyAdded) { album -> AlbumTile(album, callbacks, showPlayCount = false) }
        shelf(R.string.home_most_played, "most-played", content.mostPlayed) { album -> AlbumTile(album, callbacks, showPlayCount = true) }
        shelf(R.string.home_something_different, "something-different", content.somethingDifferent) { artist -> ArtistTile(artist, callbacks) }
    }
}

/** The current queue, to pick up where it was left: its song's album art and title, the artist and time left, Play and Shuffle. */
@Composable
private fun ResumeHero(
    resume: ResumeQueue,
    callbacks: HomeCallbacks,
) {
    val song = resume.song
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val timeLeft = stringResource(R.string.home_resume_time_left, formatDuration(resume.timeLeftMs))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LibraryArtwork(song, ArtworkPlaceholder.Album, Modifier.size(ResumeArtworkSize), size = ArtworkSize.Grid)
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_resume_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(song.album ?: unknown, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOf(song.albumArtist ?: song.friendlyArtistName ?: unknown, timeLeft).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.padding(top = 8.dp)) {
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
    }
}

private val ResumeArtworkSize = 112.dp

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
 * about two and a half fit a phone and the cut-off one says the row scrolls (#490); each title and artist sits below
 * its cover rather than over it, where they'd clash with text printed on the art (#404).
 */
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
    // The play count leads, so a long artist name is what gets cut short.
    val subtitle = if (showPlayCount) listOf(pluralString(R.plurals.home_play_count, album.playCount), artist).joinToString(" · ") else artist
    GridTile(
        title = title,
        subtitle = subtitle,
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

private val ShelfTileWidth = 140.dp
