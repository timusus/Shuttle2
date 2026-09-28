package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

/**
 * Artwork for something with none of its own (a genre, a playlist): a muted tone from a small curated palette, picked
 * by [seed], under [icon] (#646). The tone is stable across launches, so a genre keeps its colour on every screen, and
 * each has a light and a dark shade so the tile sits quietly in either theme. [size] picks the corner, as [Artwork]'s
 * does; the caller's [modifier] may override the size.
 */
@Composable
fun GeneratedArtwork(
    seed: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Grid,
    shape: ArtworkShape = ArtworkShape.Rounded,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(artworkShape(shape, size))
            .background(GeneratedArtworkColors.tone(seed, dark)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(GeneratedArtworkColors.GLYPH_SCALE),
        )
    }
}

/** Below this, the theme's surface is a dark one. */
private const val DARK_SURFACE_LUMINANCE = 0.5f

/** [GeneratedArtwork]'s palette: eight muted tones (slate, sage, sand, clay, dusk, teal, rose, ochre), light and dark. */
object GeneratedArtworkColors {
    /** The glyph's share of the artwork's side. */
    internal const val GLYPH_SCALE = 0.32f

    private const val FNV_OFFSET = 0xcbf29ce484222325uL
    private const val FNV_PRIME = 0x100000001b3uL

    private val light = listOf(0xFFD5DBE3, 0xFFD3DDD0, 0xFFE5DCCB, 0xFFE3D2CC, 0xFFDAD5E3, 0xFFCFDEDD, 0xFFE4D3D9, 0xFFE3DBC4).map(::Color)
    private val dark = listOf(0xFF3A4350, 0xFF3B4739, 0xFF4B4336, 0xFF4C3A34, 0xFF423C4E, 0xFF344847, 0xFF4B3940, 0xFF4A4330).map(::Color)

    /** The palette slot for [seed], case-insensitive: FNV-1a over its UTF-8 bytes, so iOS can pick the same one. */
    fun index(seed: String): Int {
        var hash = FNV_OFFSET
        for (byte in seed.lowercase().encodeToByteArray()) {
            hash = (hash xor byte.toUByte().toULong()) * FNV_PRIME
        }
        return (hash % light.size.toULong()).toInt()
    }

    /** [seed]'s tone in the light or [dark] theme. */
    fun tone(seed: String, dark: Boolean): Color = (if (dark) this.dark else light)[index(seed)]
}

@Preview
@Composable
private fun GeneratedArtworkPreview() {
    S2Preview {
        Row {
            GeneratedArtwork("Jazz", Icons.Rounded.LibraryMusic)
            GeneratedArtwork("Road trip", Icons.AutoMirrored.Rounded.QueueMusic, Modifier.padding(start = S2Spacing.small))
        }
    }
}
