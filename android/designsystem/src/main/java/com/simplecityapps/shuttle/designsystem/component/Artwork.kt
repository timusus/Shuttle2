package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** The artwork sizes S2 lays out: list rows, the grid tile, and the detail/player hero. */
enum class ArtworkSize(val dp: Dp) {
    Small(40.dp),
    Medium(56.dp),
    Grid(160.dp),
    Hero(240.dp),
}

/** Album, song and playlist art is a continuous rounded rectangle from the theme's scale; artist images are circles. */
enum class ArtworkShape { Rounded, Circle }

/**
 * What an artwork stands for, which picks the placeholder shown when there's no image. The smart playlists each have
 * their own ([Favorites], [RecentlyAdded], [MostPlayed], [History]); [SmartPlaylist] is the generic one.
 */
enum class ArtworkPlaceholder(internal val icon: ImageVector) {
    Song(Icons.Rounded.MusicNote),
    Album(Icons.Rounded.Album),
    Artist(Icons.Rounded.Person),
    Playlist(Icons.AutoMirrored.Rounded.QueueMusic),
    SmartPlaylist(Icons.Rounded.AutoAwesome),
    Favorites(Icons.Rounded.Favorite),
    RecentlyAdded(Icons.Rounded.LibraryAdd),
    MostPlayed(Icons.AutoMirrored.Rounded.TrendingUp),
    History(Icons.Rounded.History),
    Genre(Icons.Rounded.LibraryMusic),
    Folder(Icons.Rounded.Folder),
}

/**
 * Artwork for `@Preview`s and screenshot tests, where no image loader runs: the image for a model
 * (a sample album id, or an app model named after a sample album), drawn synchronously, or null to
 * keep the placeholder. The app never provides one; `S2Preview` provides the sample library's covers.
 */
fun interface PreviewArtwork {
    fun image(model: Any): ImageBitmap?
}

/** The [PreviewArtwork] in scope: null outside previews and screenshot tests. */
val LocalPreviewArtwork: ProvidableCompositionLocal<PreviewArtwork?> = staticCompositionLocalOf { null }

/** [model]'s image from [LocalPreviewArtwork], for callers that load their own image: null outside previews. */
@Composable
fun previewArtwork(model: Any?): ImageBitmap? = model?.let { LocalPreviewArtwork.current?.image(it) }

/**
 * A piece of artwork: [image] clipped to the artwork shape once it has loaded, an empty container
 * while [loading], otherwise the [placeholder] for the media type: its icon centred in the same container.
 * The image loader stays with the caller, which passes it through the [image] slot.
 *
 * [model] is what [image] loads. Only previews read it: when [LocalPreviewArtwork] has an image for
 * it, that image is drawn in place of [image].
 */
@Composable
fun Artwork(
    placeholder: ArtworkPlaceholder,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Medium,
    shape: ArtworkShape = ArtworkShape.Rounded,
    loading: Boolean = false,
    model: Any? = null,
    image: (@Composable () -> Unit)? = null,
) {
    val clip = artworkShape(shape, size)
    val preview = previewArtwork(model)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(clip)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when {
            preview != null -> Image(preview, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            image != null -> Box(Modifier.fillMaxSize()) { image() }
            loading -> Unit
            else -> ArtworkPlaceholderGlyph(placeholder, size)
        }
    }
}

/**
 * The [placeholder]'s glyph as [Artwork] draws it: the media type's icon, in a neutral grey (#646), centred on the
 * artwork's own container. For an [Artwork] `image` slot to draw under an image that loads asynchronously, so art
 * that never loads keeps the glyph.
 */
@Composable
fun ArtworkPlaceholderGlyph(placeholder: ArtworkPlaceholder, size: ArtworkSize) {
    Icon(
        imageVector = placeholder.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size.dp * 0.4f),
    )
}

/** Row artwork gets the small corner, tiles the large, the hero the Expressive large-increased. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun artworkShape(shape: ArtworkShape, size: ArtworkSize): Shape = when (shape) {
    ArtworkShape.Circle -> CircleShape

    ArtworkShape.Rounded -> when (size) {
        ArtworkSize.Small, ArtworkSize.Medium -> MaterialTheme.shapes.small
        ArtworkSize.Grid -> MaterialTheme.shapes.large
        ArtworkSize.Hero -> MaterialTheme.shapes.largeIncreased
    }
}

@Preview
@Composable
private fun ArtworkPreview() {
    S2Preview {
        Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Grid, model = SampleLibrary.albums.first())
    }
}
