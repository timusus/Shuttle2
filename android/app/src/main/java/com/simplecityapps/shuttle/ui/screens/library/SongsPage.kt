package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.sorting.letterSections
import com.simplecityapps.shuttle.sorting.songLetterKey
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.songThumbLabel
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab

/** Songs: every song. Tap plays from that row; long-press selects. */
@Composable
fun SongsPage(
    state: SongListUiState,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        SongListUiState.LoadingState.Loading -> LibraryContentState.Loading
        SongListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        SongListUiState.LoadingState.Empty -> LibraryContentState.Empty
        SongListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.song_list_empty), modifier, state.scanProgress, controls, hasItems = state.songs.isNotEmpty()) {
        val listState = rememberLazyListState()
        ScrollToTopOnReselect(ShellTab.Library, listState)
        val byAlbum = state.sortOrder == SongSortOrder.AlbumGroupKey || state.sortOrder == SongSortOrder.Default
        val entries = remember(state.songs, byAlbum) { songEntries(state.songs, byAlbum) }
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-songs")) {
                controlsItem(controls)
                items(entries, key = SongEntry::key, contentType = { it::class }) { entry ->
                    when (entry) {
                        is SongEntry.AlbumHeader -> SongAlbumHeader(entry.song)

                        is SongEntry.Row -> LibrarySongRow(
                            song = entry.song,
                            selected = entry.song in state.selectedSongs,
                            onClick = { onSongClick(entry.song) },
                            onLongClick = { onSongLongClick(entry.song) },
                            onMore = { onSongMore(entry.song) },
                            underAlbumHeader = byAlbum,
                        )
                    }
                }
            }
            // Indexed over the entries rather than state.letterIndex: the album headers of a by-album sort shift each
            // song's position in the list.
            val sections = remember(entries, state.sortOrder) {
                songLetterKey(state.sortOrder)?.let { key -> letterSections(entries) { entry -> key(entry.song) } }
            }
            LibraryFastScroller(
                items = entries,
                sections = sections,
                scrollableState = rememberFastScrollableState(listState),
                thumbLabel = songThumbLabel(state.sortOrder)?.let { label -> { entry: SongEntry -> label(entry.song) } },
                controls = controls,
            )
        }
    }
}

/**
 * A row of the Songs page. Sorted by album, each album's songs follow an [AlbumHeader] that shows its cover once, rather
 * than every row repeating it.
 */
private sealed interface SongEntry {
    val song: Song
    val key: Any

    /** The album [song] opens. */
    data class AlbumHeader(override val song: Song) : SongEntry {
        override val key: Any get() = "album-${song.id}"
    }

    data class Row(override val song: Song) : SongEntry {
        override val key: Any get() = song.id
    }
}

/** [songs] as rows, with an album header wherever the album changes when [byAlbum]. */
private fun songEntries(songs: List<Song>, byAlbum: Boolean): List<SongEntry> = if (!byAlbum) {
    songs.map(SongEntry::Row)
} else {
    buildList {
        songs.forEachIndexed { index, song ->
            if (index == 0 || songs[index - 1].albumGroupKey != song.albumGroupKey) add(SongEntry.AlbumHeader(song))
            add(SongEntry.Row(song))
        }
    }
}

/** The cover, name and album artist of the album the rows under it belong to. */
@Composable
private fun SongAlbumHeader(song: Song) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = S2Spacing.medium, end = S2Spacing.medium, top = S2Spacing.medium, bottom = S2Spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S2Spacing.medium),
    ) {
        LibraryArtwork(song, ArtworkPlaceholder.Album, size = ArtworkSize.Medium)
        Column(Modifier.weight(1f)) {
            S2Text(song.album.orEmpty(), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            song.albumArtist?.let {
                S2Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
