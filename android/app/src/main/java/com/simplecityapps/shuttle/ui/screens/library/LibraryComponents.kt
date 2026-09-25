package com.simplecityapps.shuttle.ui.screens.library

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholderGlyph
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.model.Song
import com.squareup.phrase.Phrase

// Pieces the Compose library screens share: artwork loaded through Glide into the catalogue's Artwork slot, the
// loading / scanning / empty states, and count text.

/** Catalogue [Artwork] showing [model]'s image (a Song, Album, AlbumArtist...) over its [placeholder]. */
@OptIn(ExperimentalGlideComposeApi::class)
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
                    GlideImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        },
    )
}

/** What a library list is showing: its content, or why it has none yet. */
enum class LibraryContentState { Loading, Scanning, Empty, Ready }

/** Shows [content] once [state] is Ready; otherwise the matching loading, scan progress or [emptyTitle] state. */
@Composable
fun LibraryContent(
    state: LibraryContentState,
    emptyTitle: String,
    modifier: Modifier = Modifier,
    scanProgress: Progress? = null,
    content: @Composable () -> Unit,
) {
    when (state) {
        LibraryContentState.Loading -> LoadingState(modifier = modifier.fillMaxSize())

        LibraryContentState.Scanning -> LoadingState(
            modifier = modifier.fillMaxSize(),
            message = stringResource(R.string.library_scan_in_progress),
            progress = scanProgress?.let { progress -> { progress.asFloat() } },
        )

        LibraryContentState.Empty -> EmptyState(title = emptyTitle, modifier = modifier.fillMaxSize())

        LibraryContentState.Ready -> content()
    }
}

/** A `{count}` plural, formatted. */
@Composable
fun pluralString(@PluralsRes id: Int, count: Int): String = Phrase.fromPlural(LocalResources.current, id, count).put("count", count).format().toString()

/** "Artist · Album", the second line of a song row outside its album. */
val Song.rowSubtitle: String
    get() = listOfNotNull(friendlyArtistName, album).filter { it.isNotBlank() }.joinToString(" · ")
