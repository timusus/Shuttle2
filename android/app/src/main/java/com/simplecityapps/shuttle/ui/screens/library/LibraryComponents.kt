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
import androidx.compose.ui.platform.LocalResources
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
import com.simplecityapps.shuttle.designsystem.component.LocalArtworkImageLoaded
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.sources.formatted

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
                // The glyph under the image, so art that's still loading or never loads isn't a blank tile.
                val reportLoaded = LocalArtworkImageLoaded.current
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ArtworkPlaceholderGlyph(placeholder, size)
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { reportLoaded(false) },
                        onSuccess = { reportLoaded(true) },
                        onError = { reportLoaded(false) },
                    )
                }
            }
        },
    )
}

/** What a library list is showing: its content, or why it has none yet. */
enum class LibraryContentState { Loading, Scanning, Empty, Ready }

/** A scan with [hasItems] keeps [content] visible under a thin progress bar instead of a full-screen placeholder. */
@Composable
fun LibraryContent(
    state: LibraryContentState,
    emptyTitle: String,
    modifier: Modifier = Modifier,
    scanProgress: Progress? = null,
    controls: LibraryTabControls? = null,
    hasItems: Boolean = false,
    emptyMessage: String? = null,
    emptyAction: StateAction? = null,
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

            LibraryContentState.Empty -> EmptyState(title = emptyTitle, message = emptyMessage, action = emptyAction, modifier = fill)

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

/**
 * A count plural with [count] in the locale's digit grouping ("8,208 songs"). The plural strings keep their `%1$d`
 * placeholder so translations stay untouched; it is swapped for `%1$s` here to take the grouped text.
 */
@Composable
fun pluralString(@PluralsRes id: Int, count: Int): String {
    val resources = LocalResources.current
    val pattern = resources.getQuantityText(id, count).toString().replace("%1\$d", "%1\$s")
    return String.format(pattern, count.formatted())
}

/** "Artist · Album", the second line of a song row outside its album. */
val Song.rowSubtitle: String
    get() = listOfNotNull(friendlyArtistName, album).filter { it.isNotBlank() }.joinToString(" · ")

/** The gap between a mosaic's covers. */
private val MosaicGap = 2.dp

/** A 2x2 mosaic of covers, repeating covers so every cell stays square and uncropped. */
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
