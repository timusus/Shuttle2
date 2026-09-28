package com.simplecityapps.shuttle.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.GeneratedArtwork
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.CoverMosaic
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.library.nameKey
import com.simplecityapps.shuttle.ui.screens.library.pluralString
import com.simplecityapps.shuttle.ui.text.stringResource as stringResourceKey

/** The kind of thing a [HomeItem] is, as its tiles label it; a smart playlist is a playlist to the user. */
enum class HomeItemKind(
    @StringRes val label: Int,
    @StringRes val goTo: Int,
) {
    Album(R.string.home_type_album, R.string.home_go_to_album),
    Artist(R.string.home_type_artist, R.string.home_go_to_artist),
    Playlist(R.string.home_type_playlist, R.string.home_go_to_playlist),
    Genre(R.string.home_type_genre, R.string.home_go_to_genre),
}

val HomeItem.kind: HomeItemKind
    get() = when (this) {
        is HomeItem.AlbumItem -> HomeItemKind.Album
        is HomeItem.ArtistItem -> HomeItemKind.Artist
        is HomeItem.PlaylistItem, is HomeItem.SmartPlaylistItem -> HomeItemKind.Playlist
        is HomeItem.GenreItem -> HomeItemKind.Genre
    }

/** The tile's test tag, as the iOS app's accessibility identifier: a smart playlist has its own. */
val HomeItem.tileTag: String
    get() = when (this) {
        is HomeItem.AlbumItem -> "homeTile.album"
        is HomeItem.ArtistItem -> "homeTile.artist"
        is HomeItem.PlaylistItem -> "homeTile.playlist"
        is HomeItem.SmartPlaylistItem -> "homeTile.smartPlaylist"
        is HomeItem.GenreItem -> "homeTile.genre"
    }

/** The songs the item stands for, as the queue actions take them. */
val HomeItem.selection: MediaSelection
    get() = when (this) {
        is HomeItem.AlbumItem -> MediaSelection.Albums(album)
        is HomeItem.ArtistItem -> MediaSelection.AlbumArtists(albumArtist)
        is HomeItem.PlaylistItem -> MediaSelection.Playlists(playlist)
        is HomeItem.SmartPlaylistItem -> MediaSelection.SongsMatching(smartPlaylistId.songQuery)
        is HomeItem.GenreItem -> MediaSelection.Genres(genre)
    }

/** Plays the item in order from its start: [HomeItem.playAction], except a genre, whose tile shuffles but whose Play plays. */
fun HomeItem.playInOrderAction(): MediaAction = when (this) {
    is HomeItem.GenreItem -> MediaAction.Play(selection, context = playContext)
    else -> playAction()
}

fun HomeItem.shuffleAction(): MediaAction = MediaAction.Shuffle(selection, playContext)

private val HomeItem.placeholder: ArtworkPlaceholder
    get() = when (this) {
        is HomeItem.AlbumItem -> ArtworkPlaceholder.Album
        is HomeItem.ArtistItem -> ArtworkPlaceholder.Artist
        is HomeItem.PlaylistItem -> ArtworkPlaceholder.Playlist
        is HomeItem.SmartPlaylistItem -> ArtworkPlaceholder.SmartPlaylist
        is HomeItem.GenreItem -> ArtworkPlaceholder.Genre
    }

@Composable
fun HomeItem.title(): String = when (this) {
    is HomeItem.AlbumItem -> album.name
    is HomeItem.ArtistItem -> albumArtist.name ?: albumArtist.friendlyArtistName
    is HomeItem.PlaylistItem -> playlist.name
    is HomeItem.SmartPlaylistItem -> stringResourceKey(smartPlaylistId.nameKey)
    is HomeItem.GenreItem -> genre.name
} ?: stringResource(com.simplecityapps.core.R.string.unknown)

/** The line under a shelf tile's title: an album's artist, an artist's album count, a playlist's or genre's song count. */
@Composable
private fun HomeItem.detail(): String? = when (this) {
    is HomeItem.AlbumItem -> album.albumArtist ?: album.friendlyArtistName
    is HomeItem.ArtistItem -> pluralString(R.plurals.albumsPlural, albumArtist.albumCount)
    is HomeItem.PlaylistItem -> pluralString(R.plurals.songsPlural, playlist.songCount)
    is HomeItem.SmartPlaylistItem -> null
    is HomeItem.GenreItem -> pluralString(R.plurals.songsPlural, genre.songCount)
}

/**
 * A shelf tile's subtitle: in a shelf of one kind just the detail, in a mixed one the kind first ("Artist · 12
 * albums"), so a row of covers says which are albums and which are artists.
 */
@Composable
fun HomeItem.subtitle(mixed: Boolean): String {
    val type = stringResource(kind.label)
    val detail = detail() ?: return type
    return if (mixed) stringResource(R.string.home_tile_subtitle, type, detail) else detail
}

/**
 * An item's artwork: an album's cover or an artist's picture, with the same corners (the owner's call, as on iOS); a
 * playlist or genre, which have no artwork of their own, gets a mosaic of its albums' [covers] (#646), else
 * [GeneratedArtwork], as a smart playlist always does.
 */
@Composable
fun HomeItemArtwork(
    item: HomeItem,
    covers: List<Song>,
    size: ArtworkSize,
    modifier: Modifier = Modifier,
) {
    when {
        item is HomeItem.AlbumItem -> LibraryArtwork(item.album, item.placeholder, modifier, size = size)
        item is HomeItem.ArtistItem -> LibraryArtwork(item.albumArtist, item.placeholder, modifier, size = size)
        covers.isNotEmpty() -> CoverMosaic(covers, item.placeholder, modifier, size = size)
        item is HomeItem.PlaylistItem -> GeneratedArtwork(item.playlist.name, Icons.AutoMirrored.Rounded.QueueMusic, modifier, size = size)
        item is HomeItem.SmartPlaylistItem -> GeneratedArtwork(item.smartPlaylistId.id, item.smartPlaylistId.icon, modifier, size = size)
        item is HomeItem.GenreItem -> GeneratedArtwork(item.genre.name, Icons.Rounded.LibraryMusic, modifier, size = size)
    }
}

private val SmartPlaylistId.icon: ImageVector
    get() = when (this) {
        SmartPlaylistId.Favourites -> Icons.Rounded.Favorite
        SmartPlaylistId.RecentlyAdded -> Icons.Rounded.LibraryAdd
        SmartPlaylistId.MostPlayed -> Icons.AutoMirrored.Rounded.TrendingUp
        SmartPlaylistId.History -> Icons.Rounded.History
    }

/**
 * What every Home tile offers besides its tap: the long-press actions sheet (Play, Shuffle, Play next, Add to queue,
 * Add to playlist and the rest, then Go to …) and the same actions for TalkBack, all carrying the item's play context.
 */
class HomeItemActions(
    val showMenu: () -> Unit,
    val accessibilityActions: List<CustomAccessibilityAction>,
)

@Composable
fun homeItemActions(
    item: HomeItem,
    callbacks: HomeCallbacks,
): HomeItemActions {
    val title = item.title()
    val detail = item.detail()
    val goTo = stringResource(item.kind.goTo)
    val play = stringResource(R.string.menu_title_play)
    val shuffle = stringResource(R.string.menu_title_shuffle)
    val playNext = stringResource(R.string.menu_title_play_next)
    val addToQueue = stringResource(R.string.menu_title_add_to_queue)
    fun act(action: () -> Unit): () -> Boolean = {
        action()
        true
    }
    return HomeItemActions(
        showMenu = {
            callbacks.onShowActions(
                MediaActionsTarget(
                    title = title,
                    subtitle = detail,
                    selection = item.selection,
                    placeholder = item.placeholder,
                    extraActions = listOf(S2Action(goTo, { callbacks.onOpenItem(item) }, Icons.AutoMirrored.Rounded.ArrowForward)),
                    playContext = item.playContext,
                ),
            )
        },
        accessibilityActions = listOf(
            CustomAccessibilityAction(play, act { callbacks.onAction(item.playInOrderAction()) }),
            CustomAccessibilityAction(shuffle, act { callbacks.onAction(item.shuffleAction()) }),
            CustomAccessibilityAction(playNext, act { callbacks.onAction(MediaAction.PlayNext(item.selection)) }),
            CustomAccessibilityAction(addToQueue, act { callbacks.onAction(MediaAction.AddToQueue(item.selection)) }),
            CustomAccessibilityAction(goTo, act { callbacks.onOpenItem(item) }),
        ),
    )
}

/**
 * A shelf tile: the item's artwork, square with the tile corner, then its title and [HomeItem.subtitle] under it, where
 * they can't clash with text printed on the art (#404). A tap opens the item, or for a genre shuffles it (a Genre pick
 * is something to put on); a long press has the rest.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeShelfTile(
    item: HomeItem,
    covers: List<Song>,
    mixed: Boolean,
    width: Dp,
    largeText: Boolean,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val actions = homeItemActions(item, callbacks)
    val lines = if (largeText) LARGE_TEXT_TILE_LINES else 1
    Column(
        modifier = modifier
            .width(width)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = { if (item is HomeItem.GenreItem) callbacks.onAction(item.playAction()) else callbacks.onOpenItem(item) },
                onLongClick = actions.showMenu,
            )
            .semantics { customActions = actions.accessibilityActions }
            .testTag(item.tileTag),
        verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny),
    ) {
        HomeItemArtwork(item, covers, ArtworkSize.Grid, Modifier.padding(bottom = S2Spacing.xsmall).fillMaxWidth().aspectRatio(1f))
        // Inset from the tile's rounded corners, which clip the press ripple: flush, they'd shave the first glyph.
        Column(
            modifier = Modifier.padding(start = S2Spacing.small, end = S2Spacing.small, bottom = S2Spacing.small),
            verticalArrangement = Arrangement.spacedBy(S2Spacing.tiny),
        ) {
            Text(
                text = item.title(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle(mixed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How many lines a tile's title may take at the largest font sizes, where one would cut off nearly every title. */
internal const val LARGE_TEXT_TILE_LINES = 3
