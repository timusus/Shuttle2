package com.simplecityapps.shuttle.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.isLargeText
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSection
import com.simplecityapps.shuttle.ui.common.downloads.offlineState
import kotlin.math.min

/** An album section of a song list: [album]'s header and its [songs], keyed apart from the page's other rows. */
internal class AlbumSongs(val album: Album, val songs: List<Song>) {
    val headerKey: String get() = "album-${album.groupKey}"

    // By position, since a playlist can hold the same song twice
    fun songKey(index: Int): String = "album-${album.groupKey}-song-$index"
}

/** Every unfolded section among [this], by the key of its header and of each of its song rows. */
internal fun List<AlbumSongs>.unfoldedByKey(expanded: Set<AlbumGroupKey>): Map<Any, AlbumSongs> = buildMap {
    this@unfoldedByKey.forEach { section ->
        if (section.album.groupKey !in expanded) return@forEach
        put(section.headerKey, section)
        section.songs.indices.forEach { put(section.songKey(it), section) }
    }
}

/**
 * [section]'s header row, then its songs while [expanded]. Song rows report their index in the play order, which
 * starts at [startIndex] for this section and runs across every section of the list.
 */
internal fun LazyListScope.albumSection(
    section: AlbumSongs,
    expanded: Boolean,
    startIndex: Int,
    currentSong: Song?,
    unknown: String,
    header: @Composable (AlbumSongs, Modifier) -> Unit,
    onPlay: (index: Int) -> Unit,
    onSongMore: (index: Int) -> Unit,
) {
    item(key = section.headerKey, contentType = "album") { header(section, Modifier) }
    if (!expanded) return
    itemsIndexed(section.songs, key = { index, _ -> section.songKey(index) }, contentType = { _, _ -> "song" }) { index, song ->
        SongRow(
            title = song.name ?: unknown,
            subtitle = song.artistUnlessAlbumArtist(section.album),
            onClick = { onPlay(startIndex + index) },
            trackNumber = song.track,
            duration = formatDuration(song.duration.toLong()),
            playing = song.id == currentSong?.id,
            offlineState = song.offlineState(),
            onMore = { onSongMore(startIndex + index) },
        )
    }
}

/**
 * Every section of a song list, in play order: album sections fold under their headers, and songs on no album
 * follow under Other Songs (with no header when they are the whole list). [looseSongs] draws those, with their
 * offset in the play order.
 */
internal fun LazyListScope.songSectionRows(
    sections: List<SongSection>,
    expanded: Set<AlbumGroupKey>,
    currentSong: Song?,
    unknown: String,
    header: @Composable (AlbumSongs, Modifier) -> Unit,
    onPlay: (index: Int) -> Unit,
    onSongMore: (index: Int) -> Unit,
    looseSongs: LazyListScope.(songs: List<Song>, startIndex: Int) -> Unit,
) {
    val grouped = sections.any { it.album != null }
    var offset = 0
    sections.forEach { section ->
        val startIndex = offset
        offset += section.songs.size
        val album = section.album
        if (album != null) {
            albumSection(AlbumSongs(album, section.songs), album.groupKey in expanded, startIndex, currentSong, unknown, header, onPlay, onSongMore)
        } else {
            if (grouped) item(key = "other-songs-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_other_songs)) }
            looseSongs(section.songs, startIndex)
        }
    }
}

/**
 * An album section's header: the album, its year and song count; a tap folds or unfolds its songs, the thumbnail opens
 * the album, and a long press or the overflow opens its actions.
 */
@Composable
internal fun AlbumSectionHeader(
    album: Album,
    songCount: Int,
    expanded: Boolean,
    unknown: String,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foldState = stringResource(if (expanded) R.string.artist_detail_album_expanded else R.string.artist_detail_album_collapsed)
    AlbumRow(
        title = album.name ?: unknown,
        artist = listOfNotNull(album.year?.toString(), pluralString(R.plurals.songsPlural, songCount)).joinToString(" · "),
        onClick = onToggle,
        // Inset so the unfolded album's selection container sits on the content inset, like the rows below it
        modifier = modifier.padding(horizontal = S2Spacing.small).semantics { stateDescription = foldState },
        edgeInset = S2Spacing.small,
        artwork = {
            val openAlbumLabel = stringResource(R.string.artist_detail_open_album, album.name ?: unknown)
            // The thumbnail opens the album; its description alone announces that, so it isn't read twice.
            LibraryArtwork(
                album,
                ArtworkPlaceholder.Album,
                Modifier
                    .semantics { contentDescription = openAlbumLabel }
                    .clickable(onClick = onOpen),
                size = ArtworkSize.Medium,
            )
        },
        selected = expanded,
        onLongClick = onMore,
        onMore = onMore,
    )
}

/** Pins the unfolded album's header below the bar; a LazyColumn sticky header would scroll out of sight behind it. */
@Composable
internal fun BoxScope.PinnedAlbumHeader(
    listState: LazyListState,
    topInset: Dp,
    sectionsByKey: Map<Any, AlbumSongs>,
    tag: String,
    header: @Composable (AlbumSongs, Modifier) -> Unit,
) {
    val obscuredPx = with(LocalDensity.current) { topInset.roundToPx() }
    val pinned by remember(listState, sectionsByKey, obscuredPx) {
        derivedStateOf { listState.pinnedSection(sectionsByKey, obscuredPx) }
    }
    var heightPx by remember { mutableIntStateOf(0) }
    val (section, nextOffset) = pinned ?: return
    Box(
        Modifier
            .padding(top = topInset)
            .fillMaxWidth()
            // Pushed up, it slides under the bar's lower edge rather than over the bar
            .clipToBounds()
            // A copy of the header in the list, which screen readers already have
            .semantics { hideFromAccessibility() }
            .testTag(tag),
    ) {
        header(
            section,
            Modifier
                .onSizeChanged { heightPx = it.height }
                .offset { IntOffset(0, nextOffset?.let { min(0, it - obscuredPx - heightPx) } ?: 0) }
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

/**
 * The unfolded section in [sectionsByKey] whose rows are under the bar ([obscuredPx] from the top of the list), with the
 * offset of the first row after it on screen, if any; null while that section's own header is still in full view.
 */
private fun LazyListState.pinnedSection(sectionsByKey: Map<Any, AlbumSongs>, obscuredPx: Int): Pair<AlbumSongs, Int?>? {
    val visible = layoutInfo.visibleItemsInfo
    val under = visible.firstOrNull { it.offset + it.size > obscuredPx } ?: return null
    val section = sectionsByKey[under.key] ?: return null
    if (under.key == section.headerKey && under.offset >= obscuredPx) return null
    val next = visible.firstOrNull { it.index > under.index && sectionsByKey[it.key] !== section }
    return section to next?.offset
}

/** A sort in a songs header's menu, with its menu label and the sort button's short one. */
internal class SortChoice<T>(val value: T, @StringRes val label: Int, @StringRes val shortLabel: Int = label)

/** A song list's header: [title], the sort button and menu over [choices], and Expand / Collapse all unless [allExpanded] is null. */
@Composable
internal fun <T> SongsHeader(
    title: String,
    sort: T,
    choices: List<SortChoice<T>>,
    allExpanded: Boolean?,
    onSortSelected: (T) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    sortTag: String,
) {
    SectionHeader(title = title) {
        Box {
            var sorting by remember { mutableStateOf(false) }
            val current = choices.firstOrNull { it.value == sort } ?: choices.first()
            val description = stringResource(R.string.library_sort_current, stringResource(current.label))
            // At large text the label gives way to the icon alone, as on the library tabs
            if (isLargeText()) {
                S2IconButton(
                    icon = Icons.Rounded.SwapVert,
                    contentDescription = description,
                    onClick = { sorting = true },
                    modifier = Modifier.testTag(sortTag),
                )
            } else {
                S2Button(
                    text = stringResource(current.shortLabel),
                    onClick = { sorting = true },
                    style = S2ButtonStyle.Text,
                    icon = Icons.Rounded.SwapVert,
                    modifier = Modifier
                        .heightIn(min = S2TouchTarget.minimum)
                        .semantics { contentDescription = description }
                        .testTag(sortTag),
                )
            }
            S2Menu(
                expanded = sorting,
                onDismissRequest = { sorting = false },
                groups = listOf(sortOptions(sort, choices.map { it.value to it.label }, onSortSelected)),
            )
        }
        if (allExpanded != null) {
            val label = stringResource(if (allExpanded) R.string.artist_detail_collapse_all else R.string.artist_detail_expand_all)
            Spacer(Modifier.width(S2Spacing.small))
            S2IconButton(
                icon = if (allExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                contentDescription = label,
                onClick = if (allExpanded) onCollapseAll else onExpandAll,
            )
        }
    }
}
