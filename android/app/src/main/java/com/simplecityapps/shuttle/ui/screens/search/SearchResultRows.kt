package com.simplecityapps.shuttle.ui.screens.search

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
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
    SearchResultRow(
        title = highlighted(name, hit),
        supporting = AnnotatedString(countString(R.plurals.albumsPlural, artist.albumCount)),
        onClick = { callbacks.onArtistClick(artist) },
        leading = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, size = ArtworkSize.Small, shape = ArtworkShape.Circle) },
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
    SearchResultRow(
        title = highlighted(title, hit),
        supporting = highlighted(artist, hit),
        meta = album.year?.toString(),
        onClick = { callbacks.onAlbumClick(album) },
        leading = { LibraryArtwork(album, ArtworkPlaceholder.Album, size = ArtworkSize.Small) },
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
    SearchResultRow(
        title = highlighted(title, hit),
        supporting = highlighted(subtitleParts, hit),
        onClick = { callbacks.onSongClick(index) },
        leading = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun GenreResult(hit: SearchHit<Genre>, callbacks: SearchCallbacks) {
    val genre = hit.item
    val songCount = countString(R.plurals.songsPlural, genre.songCount)
    val showActions = { callbacks.onShowActions(MediaActionsTarget(genre.name, songCount, MediaSelection.Genres(genre), ArtworkPlaceholder.Genre)) }
    SearchResultRow(
        title = highlighted(genre.name, hit),
        supporting = AnnotatedString(songCount),
        onClick = { callbacks.onGenreClick(genre) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

@Composable
internal fun PlaylistResult(hit: SearchHit<Playlist>, callbacks: SearchCallbacks) {
    val playlist = hit.item
    val summary = countString(R.plurals.songsPlural, playlist.songCount)
    val showActions = { callbacks.onShowActions(MediaActionsTarget(playlist.name, summary, MediaSelection.Playlists(playlist), ArtworkPlaceholder.Playlist)) }
    SearchResultRow(
        title = highlighted(playlist.name, hit),
        supporting = AnnotatedString(summary),
        onClick = { callbacks.onPlaylistClick(playlist) },
        onLongClick = showActions,
        onMore = showActions,
    )
}

/**
 * The design system's library row, with styled text so the query's matches show in bold. Mirrors the design system's
 * internal `MediaRow`: title `bodyLarge`, one-line supporting text, meta `labelMedium`, an overflow button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResultRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    supporting: AnnotatedString? = null,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = leading,
        supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = if (meta != null || onMore != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    meta?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    onMore?.let { S2IconButton(Icons.Rounded.MoreVert, stringResource(com.simplecityapps.shuttle.designsystem.R.string.ds_more_options), it) }
                }
            }
        } else {
            null
        },
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val MatchStyle = SpanStyle(fontWeight = FontWeight.Bold)

/** [parts] joined with " · ", each part's matches in bold. */
@Composable
private fun highlighted(parts: List<String>, hit: SearchHit<*>): AnnotatedString = remember(parts, hit) {
    buildAnnotatedString {
        parts.forEachIndexed { i, part ->
            if (i > 0) append(" · ")
            val start = length
            append(part)
            hit.highlights(part).forEach { addStyle(MatchStyle, start + it.first, start + it.last + 1) }
        }
    }
}

@Composable
private fun highlighted(text: String, hit: SearchHit<*>): AnnotatedString = highlighted(listOf(text), hit)

@Composable
private fun countString(@PluralsRes plural: Int, count: Int): String = pluralStringResource(plural, count, count).replace("{count}", count.toString())
