package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/** The artwork sizes S2 lays out: list rows, the grid tile, and the detail/player hero. */
enum class ArtworkSize(val dp: Dp) {
    Small(40.dp),
    Medium(56.dp),
    Grid(160.dp),
    Hero(240.dp),
}

/**
 * Album and song art is a rounded rectangle; artist images are circles. [Scalloped] is the one
 * `MaterialShapes` mask, an option for playlist art only (a user image or a mosaic, never an album
 * cover).
 */
enum class ArtworkShape { Rounded, Circle, Scalloped }

/** What an artwork stands for, which picks the placeholder shown when there's no image. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class ArtworkPlaceholder(internal val icon: ImageVector, internal val polygon: RoundedPolygon) {
    Song(Icons.Rounded.MusicNote, MaterialShapes.Cookie9Sided),
    Album(Icons.Rounded.Album, MaterialShapes.Cookie12Sided),
    Artist(Icons.Rounded.Person, MaterialShapes.Flower),
    Playlist(Icons.AutoMirrored.Rounded.QueueMusic, MaterialShapes.Clover4Leaf),
    SmartPlaylist(Icons.Rounded.AutoAwesome, MaterialShapes.Sunny),
    Genre(Icons.Rounded.LibraryMusic, MaterialShapes.Pentagon),
    Folder(Icons.Rounded.Folder, MaterialShapes.Square),
}

/**
 * A piece of artwork: [image] clipped to the artwork shape once it has loaded, an empty container
 * while [loading], otherwise the [placeholder] for the media type in a `MaterialShapes` container.
 * The image loader stays with the caller, which passes it through the [image] slot.
 */
@Composable
fun Artwork(
    placeholder: ArtworkPlaceholder,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Medium,
    shape: ArtworkShape = ArtworkShape.Rounded,
    loading: Boolean = false,
    image: (@Composable () -> Unit)? = null,
) {
    val clip = artworkShape(shape, size)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(clip)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> Box(Modifier.fillMaxSize()) { image() }
            loading -> Unit
            else -> ArtworkPlaceholderContent(placeholder, size)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtworkPlaceholderContent(placeholder: ArtworkPlaceholder, size: ArtworkSize) {
    Box(
        modifier = Modifier
            .size(size.dp * 0.7f)
            .clip(placeholder.polygon.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholder.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size.dp * 0.35f),
        )
    }
}

/** Row artwork gets the small corner, tiles the large, the hero the Expressive large-increased. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun artworkShape(shape: ArtworkShape, size: ArtworkSize): Shape = when (shape) {
    ArtworkShape.Circle -> CircleShape

    ArtworkShape.Scalloped -> MaterialShapes.Cookie12Sided.toShape()

    ArtworkShape.Rounded -> when (size) {
        ArtworkSize.Small, ArtworkSize.Medium -> MaterialTheme.shapes.small
        ArtworkSize.Grid -> MaterialTheme.shapes.large
        ArtworkSize.Hero -> MaterialTheme.shapes.largeIncreased
    }
}

@Preview
@Composable
private fun ArtworkPreview() {
    S2Theme {
        Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Grid)
    }
}
