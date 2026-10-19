package com.simplecityapps.shuttle.ui.screens.search

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.GenreRow
import com.simplecityapps.shuttle.designsystem.component.PlaylistRow
import com.simplecityapps.shuttle.designsystem.component.SearchTopResult
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.common.downloads.offlineState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.library.pluralString
import com.simplecityapps.shuttle.ui.screens.library.route
import com.simplecityapps.shuttle.ui.shell.albumArtworkKey
import com.simplecityapps.shuttle.ui.shell.sharedArtwork

// Each row renders the hit at its index of its group in [results]; what a tap, Play or the actions do is
// SearchEvents.kt's and SearchResultItems.kt's.
// Top results repeat a list row's item, so only the rows share artwork: a key may appear once per screen.
@Composable
internal fun ArtistResult(results: SearchResults, index: Int, onEvent: (SearchEvent) -> Unit) {
    val hit = results.artists[index]
    val artist = hit.item
    val name = artist.name ?: artist.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(name, null, results.selection(SearchCategory.Artists, index), ArtworkPlaceholder.Artist))) }
    ArtistRow(
        name = highlighted(hit, listOf(name)).single(),
        summary = artist.summary(),
        onClick = { onEvent(results.tapEvent(SearchCategory.Artists, index)) },
        artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, size = ArtworkSize.Small, shape = ArtworkShape.Circle) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun AlbumResult(results: SearchResults, index: Int, onEvent: (SearchEvent) -> Unit) {
    val hit = results.albums[index]
    val album = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val title = album.name ?: unknown
    val artist = album.albumArtist ?: album.friendlyArtistName ?: unknown
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(title, artist, results.selection(SearchCategory.Albums, index), ArtworkPlaceholder.Album))) }
    val (titleText, artistText) = highlighted(hit, listOf(title), listOf(artist))
    AlbumRow(
        title = titleText,
        artist = artistText,
        meta = album.year?.toString(),
        onClick = { onEvent(results.tapEvent(SearchCategory.Albums, index)) },
        artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.sharedArtwork(albumArtworkKey(album.route)), size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun SongResult(results: SearchResults, index: Int, onEvent: (SearchEvent) -> Unit) {
    val hit = results.songs[index]
    val song = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val title = song.name ?: unknown
    val subtitleParts = listOfNotNull(song.friendlyArtistName ?: song.albumArtist, song.album).ifEmpty { listOf(unknown) }
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(title, subtitleParts.joinToString(" · "), results.selection(SearchCategory.Songs, index), ArtworkPlaceholder.Song))) }
    val (titleText, subtitleText) = highlighted(hit, listOf(title), subtitleParts)
    SongRow(
        title = titleText,
        subtitle = subtitleText,
        onClick = { onEvent(results.tapEvent(SearchCategory.Songs, index)) },
        artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
        offlineState = song.offlineState(),
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun GenreResult(results: SearchResults, index: Int, onEvent: (SearchEvent) -> Unit) {
    val hit = results.genres[index]
    val genre = hit.item
    val songCount = countString(R.plurals.songsPlural, genre.songCount)
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(genre.name, songCount, results.selection(SearchCategory.Genres, index), ArtworkPlaceholder.Genre))) }
    GenreRow(
        name = highlighted(hit, listOf(genre.name)).single(),
        songCount = songCount,
        onClick = { onEvent(results.tapEvent(SearchCategory.Genres, index)) },
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Genre, size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun PlaylistResult(results: SearchResults, index: Int, onEvent: (SearchEvent) -> Unit) {
    val hit = results.playlists[index]
    val playlist = hit.item
    val summary = countString(R.plurals.songsPlural, playlist.songCount)
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(playlist.name, summary, results.selection(SearchCategory.Playlists, index), ArtworkPlaceholder.Playlist))) }
    PlaylistRow(
        name = highlighted(hit, listOf(playlist.name)).single(),
        summary = summary,
        onClick = { onEvent(results.tapEvent(SearchCategory.Playlists, index)) },
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Playlist, size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun ArtistTopResult(results: SearchResults, onEvent: (SearchEvent) -> Unit) {
    val hit = results.artists.first()
    val artist = hit.item
    val name = artist.name ?: artist.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(name, null, results.selection(SearchCategory.Artists, 0), ArtworkPlaceholder.Artist))) }
    SearchTopResult(
        kind = stringResource(R.string.search_kind_artist),
        title = highlighted(hit, listOf(name)).single(),
        subtitle = AnnotatedString(artist.summary()),
        playLabel = stringResource(R.string.menu_title_play),
        playDescription = stringResource(R.string.search_play_item, name),
        onClick = { onEvent(results.tapEvent(SearchCategory.Artists, 0)) },
        onPlay = { onEvent(results.topPlayEvent(SearchCategory.Artists)) },
        onLongClick = showActions,
        onMore = showActions,
        artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, size = ArtworkSize.Feature, shape = ArtworkShape.Circle) },
    )
}

@Composable
internal fun AlbumTopResult(results: SearchResults, onEvent: (SearchEvent) -> Unit) {
    val hit = results.albums.first()
    val album = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val name = album.name ?: unknown
    val artistName = album.albumArtist ?: album.friendlyArtistName ?: unknown
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(name, artistName, results.selection(SearchCategory.Albums, 0), ArtworkPlaceholder.Album))) }
    val (title, artist) = highlighted(hit, listOf(name), listOf(artistName))
    SearchTopResult(
        kind = stringResource(R.string.search_kind_album),
        title = title,
        subtitle = artist,
        playLabel = stringResource(R.string.menu_title_play),
        playDescription = stringResource(R.string.search_play_item, name),
        onClick = { onEvent(results.tapEvent(SearchCategory.Albums, 0)) },
        onPlay = { onEvent(results.topPlayEvent(SearchCategory.Albums)) },
        onLongClick = showActions,
        onMore = showActions,
        artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, size = ArtworkSize.Feature) },
    )
}

@Composable
internal fun SongTopResult(results: SearchResults, onEvent: (SearchEvent) -> Unit) {
    val hit = results.songs.first()
    val song = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val name = song.name ?: unknown
    val subtitleParts = listOfNotNull(song.friendlyArtistName ?: song.albumArtist, song.album).ifEmpty { listOf(unknown) }
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(name, subtitleParts.joinToString(" · "), results.selection(SearchCategory.Songs, 0), ArtworkPlaceholder.Song))) }
    val (title, subtitle) = highlighted(hit, listOf(name), subtitleParts)
    SearchTopResult(
        kind = stringResource(R.string.search_kind_song),
        title = title,
        subtitle = subtitle,
        playLabel = stringResource(R.string.menu_title_play),
        playDescription = stringResource(R.string.search_play_item, name),
        onClick = { onEvent(results.tapEvent(SearchCategory.Songs, 0)) },
        onPlay = { onEvent(results.topPlayEvent(SearchCategory.Songs)) },
        onLongClick = showActions,
        onMore = showActions,
        artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Feature) },
    )
}

@Composable
internal fun GenreTopResult(results: SearchResults, onEvent: (SearchEvent) -> Unit) {
    val hit = results.genres.first()
    val genre = hit.item
    val songCount = countString(R.plurals.songsPlural, genre.songCount)
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(genre.name, songCount, results.selection(SearchCategory.Genres, 0), ArtworkPlaceholder.Genre))) }
    SearchTopResult(
        kind = stringResource(R.string.search_kind_genre),
        title = highlighted(hit, listOf(genre.name)).single(),
        subtitle = AnnotatedString(songCount),
        playLabel = stringResource(R.string.menu_title_play),
        playDescription = stringResource(R.string.search_play_item, genre.name),
        onClick = { onEvent(results.tapEvent(SearchCategory.Genres, 0)) },
        onPlay = { onEvent(results.topPlayEvent(SearchCategory.Genres)) },
        onLongClick = showActions,
        onMore = showActions,
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Genre, size = ArtworkSize.Feature) },
    )
}

@Composable
internal fun PlaylistTopResult(results: SearchResults, onEvent: (SearchEvent) -> Unit) {
    val hit = results.playlists.first()
    val playlist = hit.item
    val songCount = countString(R.plurals.songsPlural, playlist.songCount)
    val showActions = { onEvent(SearchEvent.ShowActions(MediaActionsTarget(playlist.name, songCount, results.selection(SearchCategory.Playlists, 0), ArtworkPlaceholder.Playlist))) }
    SearchTopResult(
        kind = stringResource(R.string.search_kind_playlist),
        title = highlighted(hit, listOf(playlist.name)).single(),
        subtitle = AnnotatedString(songCount),
        playLabel = stringResource(R.string.menu_title_play),
        playDescription = stringResource(R.string.search_play_item, playlist.name),
        onClick = { onEvent(results.tapEvent(SearchCategory.Playlists, 0)) },
        onPlay = { onEvent(results.topPlayEvent(SearchCategory.Playlists)) },
        onLongClick = showActions,
        onMore = showActions,
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Playlist, size = ArtworkSize.Feature) },
    )
}

private val MatchStyle = SpanStyle(fontWeight = FontWeight.Bold)

/** Each of a row's [lines] with the query's matches in bold; where they fall is [highlightedLines]'. */
@Composable
private fun highlighted(hit: SearchHit<*>, vararg lines: List<String>): List<AnnotatedString> {
    val key = lines.toList()
    return remember(hit, key) {
        highlightedLines(hit, key).map { line ->
            buildAnnotatedString {
                append(line.text)
                line.matches.forEach { addStyle(MatchStyle, it.first, it.last + 1) }
            }
        }
    }
}

@Composable
private fun AlbumArtist.summary(): String = resultCount().let {
    countString(if (it.kind == ResultCount.Kind.Albums) R.plurals.albumsPlural else R.plurals.songsPlural, it.count)
}

@Composable
private fun countString(@PluralsRes plural: Int, count: Int): String = pluralString(plural, count)
