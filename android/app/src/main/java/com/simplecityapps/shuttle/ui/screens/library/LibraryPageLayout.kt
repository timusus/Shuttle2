package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.S2GridTile
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.LetterSection
import com.simplecityapps.shuttle.ui.common.components.AlphabetFastScroller
import com.simplecityapps.shuttle.ui.common.components.FastScrollableState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.NoPopup
import com.simplecityapps.shuttle.ui.common.downloads.offlineState

// The library tabs' pages: state in, events out, restyled with catalogue rows. The ViewModels are the existing
// tab ViewModels; LibraryScreen wires them.

/**
 * Fills the page so the scroller's track sits at its end edge, as the legacy lists have it. The track starts below the
 * page's [controls] row, the list's first item, so the resting thumb never covers its Play or Shuffle button.
 */
internal fun fastScrollerModifier(controls: LibraryTabControls?) = Modifier
    .fillMaxSize()
    .padding(top = S2Spacing.small + controlsRowHeight(controls), bottom = S2Spacing.small)
    .testTag("library-fast-scroller")

/**
 * The fast scroller for a page of [items]: by first letter over [sections] when the sort is by a name, else a
 * plain thumb with [thumbLabel] in its popup, or none. The list leads with the
 * page's [controls] row, which the scroller's indices skip.
 */
@Composable
internal fun <T> LibraryFastScroller(
    items: List<T>,
    sections: List<LetterSection>?,
    scrollableState: FastScrollableState,
    thumbLabel: ((T) -> String?)? = null,
    controls: LibraryTabControls? = null,
) {
    val itemOffset = controlsItemCount(controls)
    if (sections != null) {
        AlphabetFastScroller(sections, scrollableState, fastScrollerModifier(controls), itemOffset)
    } else {
        FastScroller(
            getPopupText = { index -> items.getOrNull(index - itemOffset)?.let { thumbLabel?.invoke(it) } },
            scrollableState = scrollableState,
            modifier = fastScrollerModifier(controls),
            popup = if (thumbLabel == null) ::NoPopup else null,
        )
    }
}

/** Each smart playlist's own placeholder, so the four don't share one icon. */
internal val SmartPlaylist.placeholder: ArtworkPlaceholder
    get() = when (id) {
        SmartPlaylistId.Favourites -> ArtworkPlaceholder.Favorites
        SmartPlaylistId.RecentlyAdded -> ArtworkPlaceholder.RecentlyAdded
        SmartPlaylistId.MostPlayed -> ArtworkPlaceholder.MostPlayed
        SmartPlaylistId.History -> ArtworkPlaceholder.History
        SmartPlaylistId.Downloaded -> ArtworkPlaceholder.Downloaded
    }

/**
 * The catalogue's grid: as many columns as fit tiles of [S2GridTile.minimumWidth] scaled with the font, so a
 * phone shows two at the default size and one at 2.0, where two would cut every title to a word. The compact grid
 * fits tiles of [S2GridTile.compactMinimumWidth] the same way, three on a phone.
 */
@Composable
internal fun libraryGridColumns(compact: Boolean): GridCells = GridCells.Adaptive(
    minSize = (if (compact) S2GridTile.compactMinimumWidth else S2GridTile.minimumWidth) * LocalDensity.current.fontScale,
)

/** The gap between grid tiles, tighter in the compact grid. */
internal fun gridSpacing(compact: Boolean): Dp = if (compact) S2Spacing.xsmall else S2Spacing.small

internal val GridHorizontalPadding = S2Spacing.medium

/** A grid's padding: [top] above the first tiles, or none when the controls row leads the grid. */
internal fun gridPadding(controls: LibraryTabControls?, top: Dp) = PaddingValues(
    start = GridHorizontalPadding,
    end = GridHorizontalPadding,
    top = if (controlsItemCount(controls) > 0) 0.dp else top,
    bottom = S2Spacing.medium,
)

/**
 * A song in a list: artwork and "artist · album" outside its album; under an album header, the track number and the
 * artist, since the header already shows the cover and album.
 */
@Composable
fun LibrarySongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    underAlbumHeader: Boolean = false,
) {
    SongRow(
        title = song.name.orEmpty(),
        subtitle = if (underAlbumHeader) (song.friendlyArtistName ?: song.albumArtist).orEmpty() else song.rowSubtitle,
        onClick = onClick,
        modifier = modifier,
        artwork = if (underAlbumHeader) null else ({ LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) }),
        trackNumber = if (underAlbumHeader) song.track else null,
        duration = formatDuration(song.duration.toLong()),
        playing = playing,
        selected = selected,
        offlineState = song.offlineState(),
        onLongClick = onLongClick,
        onMore = onMore,
    )
}
