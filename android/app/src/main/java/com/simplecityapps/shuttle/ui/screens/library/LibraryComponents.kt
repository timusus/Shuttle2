package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.compose.ReportDrawnWhen
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholderGlyph
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.BarLoadingIndicator
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.model.Song

// Pieces the Compose library screens share: artwork loaded through Coil into the catalogue's Artwork slot, the
// loading / scanning / empty states, and count text.

/** Catalogue [Artwork] showing [model]'s image (a Song, Album, AlbumArtist...) over its [placeholder]. */
@Composable
fun LibraryArtwork(
    model: Any?,
    placeholder: ArtworkPlaceholder,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Medium,
    shape: ArtworkShape = ArtworkShape.Rounded,
) {
    Artwork(
        placeholder = placeholder,
        modifier = modifier,
        size = size,
        shape = shape,
        model = model,
        image = model?.let {
            {
                // The glyph under the image, so art that's still loading or never loads isn't a blank tile (#398).
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ArtworkPlaceholderGlyph(placeholder, size)
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        },
    )
}

/** What a library list is showing: its content, or why it has none yet. */
enum class LibraryContentState { Loading, Scanning, Empty, Ready }

/**
 * Shows [content] once [state] is Ready; otherwise the matching loading, scan progress or [emptyTitle] state, under the
 * tab's [controls] row when it has one (the Ready content shows that row as its list's first item). A scan with
 * [hasItems] keeps showing [content] under a thin progress bar instead of the full-screen placeholder (#625).
 */
@Composable
fun LibraryContent(
    state: LibraryContentState,
    emptyTitle: String,
    modifier: Modifier = Modifier,
    scanProgress: Progress? = null,
    controls: LibraryTabControls? = null,
    hasItems: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Library is the default start tab, so the first page past Loading is when the app is fully drawn (TTFD)
    ReportDrawnWhen { state != LibraryContentState.Loading }
    // Ready and a scan over existing items share one call site for [content], so its remembered list state (the
    // scroll position) survives the scan starting and finishing.
    if (state == LibraryContentState.Ready || (state == LibraryContentState.Scanning && hasItems)) {
        Box {
            content()
            if (state == LibraryContentState.Scanning) {
                BarLoadingIndicator(
                    description = stringResource(R.string.library_scan_in_progress),
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    progress = scanProgress?.indicatorProgress(),
                )
            }
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        if (controls != null && !controls.isEmpty) LibraryControlsRow(controls)
        val fill = Modifier.fillMaxWidth().weight(1f)
        when (state) {
            LibraryContentState.Loading -> LoadingState(modifier = fill)

            LibraryContentState.Scanning -> LoadingState(
                modifier = fill,
                message = stringResource(R.string.library_scan_in_progress),
                progress = scanProgress?.indicatorProgress(),
            )

            LibraryContentState.Empty -> EmptyState(title = emptyTitle, modifier = fill)

            LibraryContentState.Ready -> Unit
        }
    }
}

/** The fraction done, for a determinate indicator; null (indeterminate) while the total isn't known. */
private fun Progress.indicatorProgress(): (() -> Float)? = if (total > 0) {
    { asFloat() }
} else {
    null
}

/** A count plural, formatted. */
@Composable
fun pluralString(@PluralsRes id: Int, count: Int): String = pluralStringResource(id, count, count)

/** "Artist · Album", the second line of a song row outside its album. */
val Song.rowSubtitle: String
    get() = listOfNotNull(friendlyArtistName, album).filter { it.isNotBlank() }.joinToString(" · ")

/** The gap between a mosaic's covers. */
private val MosaicGap = 2.dp

/**
 * Artwork from [covers] (#491, #646, #135): a 2x2 mosaic of four albums' covers, or fewer repeated so every cell stays a
 * square, uncropped cover ([mosaicCells]); the one cover alone, or [placeholder] with none. [size] is the whole mosaic's;
 * the caller's [modifier] may override it, as for [Artwork].
 */
@Composable
fun CoverMosaic(
    covers: List<Song>,
    placeholder: ArtworkPlaceholder,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Medium,
) {
    if (covers.size < 2) {
        LibraryArtwork(covers.firstOrNull(), placeholder, modifier, size = size)
        return
    }
    val cellSize = if (size == ArtworkSize.Grid || size == ArtworkSize.Hero) ArtworkSize.Medium else ArtworkSize.Small
    val gap = Arrangement.spacedBy(MosaicGap)
    Column(modifier.size(size.dp), verticalArrangement = gap) {
        mosaicCells(covers).chunked(2).forEach { pair ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = gap) {
                pair.forEach { song -> LibraryArtwork(song, ArtworkPlaceholder.Album, Modifier.weight(1f).fillMaxHeight(), size = cellSize) }
            }
        }
    }
}

/**
 * The four cells of a 2x2 mosaic, row by row, for two to four [covers]: two alternate as a checkerboard (A B / B A),
 * three repeat the first (A B / C A), four fill it as they are.
 */
internal fun mosaicCells(covers: List<Song>): List<Song> = when (covers.size) {
    2 -> listOf(covers[0], covers[1], covers[1], covers[0])
    3 -> listOf(covers[0], covers[1], covers[2], covers[0])
    else -> covers.take(4)
}
