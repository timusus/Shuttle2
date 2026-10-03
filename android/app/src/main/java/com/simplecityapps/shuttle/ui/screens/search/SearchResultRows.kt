package com.simplecityapps.shuttle.ui.screens.search

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
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
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork

@Composable
internal fun ArtistResult(hit: SearchHit<AlbumArtist>, callbacks: SearchCallbacks) {
    val artist = hit.item
    val name = artist.name ?: artist.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
    val showActions = { callbacks.onShowActions(MediaActionsTarget(name, null, MediaSelection.AlbumArtists(artist), ArtworkPlaceholder.Artist)) }
    ArtistRow(
        name = highlighted(hit, listOf(name)).single(),
        // A track artist found by their credits (#637) has songs but no albums of their own to count
        summary = if (artist.isAlbumArtist) countString(R.plurals.albumsPlural, artist.albumCount) else countString(R.plurals.songsPlural, artist.songCount),
        onClick = { callbacks.onArtistClick(artist) },
        artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, size = ArtworkSize.Small, shape = ArtworkShape.Circle) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun AlbumResult(hit: SearchHit<Album>, callbacks: SearchCallbacks) {
    val album = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val title = album.name ?: unknown
    val artist = album.albumArtist ?: album.friendlyArtistName ?: unknown
    val showActions = { callbacks.onShowActions(MediaActionsTarget(title, artist, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) }
    val (titleText, artistText) = highlighted(hit, listOf(title), listOf(artist))
    AlbumRow(
        title = titleText,
        artist = artistText,
        meta = album.year?.toString(),
        onClick = { callbacks.onAlbumClick(album) },
        artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

/** A song result; [index] is its place among the song results, which tapping plays from. */
@Composable
internal fun SongResult(hit: SearchHit<Song>, index: Int, callbacks: SearchCallbacks) {
    val song = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val title = song.name ?: unknown
    val subtitleParts = listOfNotNull(song.friendlyArtistName ?: song.albumArtist, song.album).ifEmpty { listOf(unknown) }
    val showActions = { callbacks.onShowActions(MediaActionsTarget(title, subtitleParts.joinToString(" · "), MediaSelection.Songs(song), ArtworkPlaceholder.Song)) }
    val (titleText, subtitleText) = highlighted(hit, listOf(title), subtitleParts)
    SongRow(
        title = titleText,
        subtitle = subtitleText,
        onClick = { callbacks.onSongClick(index) },
        artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun GenreResult(hit: SearchHit<Genre>, callbacks: SearchCallbacks) {
    val genre = hit.item
    val songCount = countString(R.plurals.songsPlural, genre.songCount)
    val showActions = { callbacks.onShowActions(MediaActionsTarget(genre.name, songCount, MediaSelection.Genres(genre), ArtworkPlaceholder.Genre)) }
    GenreRow(
        name = highlighted(hit, listOf(genre.name)).single(),
        songCount = songCount,
        onClick = { callbacks.onGenreClick(genre) },
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Genre, size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun PlaylistResult(hit: SearchHit<Playlist>, callbacks: SearchCallbacks) {
    val playlist = hit.item
    val summary = countString(R.plurals.songsPlural, playlist.songCount)
    val showActions = { callbacks.onShowActions(MediaActionsTarget(playlist.name, summary, MediaSelection.Playlists(playlist), ArtworkPlaceholder.Playlist)) }
    PlaylistRow(
        name = highlighted(hit, listOf(playlist.name)).single(),
        summary = summary,
        onClick = { callbacks.onPlaylistClick(playlist) },
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Playlist, size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun ArtistTopResult(hit: SearchHit<AlbumArtist>, callbacks: SearchCallbacks) {
    val artist = hit.item
    val name = artist.name ?: artist.friendlyArtistName ?: stringResource(com.simplecityapps.core.R.string.unknown)
    SearchTopResult(
        kind = stringResource(R.string.search_kind_artist),
        title = highlighted(hit, listOf(name)).single(),
        subtitle = AnnotatedString(if (artist.isAlbumArtist) countString(R.plurals.albumsPlural, artist.albumCount) else countString(R.plurals.songsPlural, artist.songCount)),
        playLabel = stringResource(R.string.menu_title_play),
        onClick = { callbacks.onArtistClick(artist) },
        onPlay = { callbacks.onPlay(MediaSelection.AlbumArtists(artist)) },
        artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, size = ArtworkSize.Feature, shape = ArtworkShape.Circle) },
    )
}

@Composable
internal fun AlbumTopResult(hit: SearchHit<Album>, callbacks: SearchCallbacks) {
    val album = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val (title, artist) = highlighted(hit, listOf(album.name ?: unknown), listOf(album.albumArtist ?: album.friendlyArtistName ?: unknown))
    SearchTopResult(
        kind = stringResource(R.string.search_kind_album),
        title = title,
        subtitle = artist,
        playLabel = stringResource(R.string.menu_title_play),
        onClick = { callbacks.onAlbumClick(album) },
        onPlay = { callbacks.onPlay(MediaSelection.Albums(album)) },
        artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, size = ArtworkSize.Feature) },
    )
}

/** The top song result; playing it plays that song alone, as [SongResult] plays from its [index]. */
@Composable
internal fun SongTopResult(hit: SearchHit<Song>, index: Int, callbacks: SearchCallbacks) {
    val song = hit.item
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val subtitleParts = listOfNotNull(song.friendlyArtistName ?: song.albumArtist, song.album).ifEmpty { listOf(unknown) }
    val (title, subtitle) = highlighted(hit, listOf(song.name ?: unknown), subtitleParts)
    SearchTopResult(
        kind = stringResource(R.string.search_kind_song),
        title = title,
        subtitle = subtitle,
        playLabel = stringResource(R.string.menu_title_play),
        onClick = { callbacks.onSongClick(index) },
        onPlay = { callbacks.onSongClick(index) },
        artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Feature) },
    )
}

@Composable
internal fun GenreTopResult(hit: SearchHit<Genre>, callbacks: SearchCallbacks) {
    val genre = hit.item
    SearchTopResult(
        kind = stringResource(R.string.search_kind_genre),
        title = highlighted(hit, listOf(genre.name)).single(),
        subtitle = AnnotatedString(countString(R.plurals.songsPlural, genre.songCount)),
        playLabel = stringResource(R.string.menu_title_play),
        onClick = { callbacks.onGenreClick(genre) },
        onPlay = { callbacks.onPlay(MediaSelection.Genres(genre)) },
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Genre, size = ArtworkSize.Feature) },
    )
}

@Composable
internal fun PlaylistTopResult(hit: SearchHit<Playlist>, callbacks: SearchCallbacks) {
    val playlist = hit.item
    SearchTopResult(
        kind = stringResource(R.string.search_kind_playlist),
        title = highlighted(hit, listOf(playlist.name)).single(),
        subtitle = AnnotatedString(countString(R.plurals.songsPlural, playlist.songCount)),
        playLabel = stringResource(R.string.menu_title_play),
        onClick = { callbacks.onPlaylistClick(playlist) },
        onPlay = { callbacks.onPlay(MediaSelection.Playlists(playlist)) },
        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Playlist, size = ArtworkSize.Feature) },
    )
}

private val MatchStyle = SpanStyle(fontWeight = FontWeight.Bold)

/**
 * Each of a row's [lines] with the query's matches in bold, a line's parts joined with " · ". The lines are highlighted
 * together, so a query token is bolded only where it matches best in the row.
 */
@Composable
private fun highlighted(hit: SearchHit<*>, vararg lines: List<String>): List<AnnotatedString> {
    val key = lines.toList()
    return remember(hit, key) {
        val ranges = hit.highlights(key.flatten()).iterator()
        key.map { parts ->
            buildAnnotatedString {
                parts.forEachIndexed { i, part ->
                    if (i > 0) append(" · ")
                    val start = length
                    append(part)
                    ranges.next().forEach { addStyle(MatchStyle, start + it.first, start + it.last + 1) }
                }
            }
        }
    }
}

@Composable
private fun countString(@PluralsRes plural: Int, count: Int): String = pluralStringResource(plural, count, count).replace("{count}", count.toString())
