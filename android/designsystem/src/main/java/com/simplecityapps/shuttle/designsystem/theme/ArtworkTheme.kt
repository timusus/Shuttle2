package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Where an artwork seed is in its extraction, as seen by [ArtworkTheme]. */
@Immutable
sealed interface ArtworkSeed {
    /** Extraction is in flight: keep showing the previous seed. */
    data object Loading : ArtworkSeed

    /** No artwork, or extraction failed: fall back to the root scheme. */
    data object None : ArtworkSeed

    data class Available(val color: Color) : ArtworkSeed
}

/**
 * Nests an artwork-seeded scheme under [S2Theme] for the player and artwork detail screens.
 *
 * A seed too grey to carry a hue, [ArtworkSeed.None], or a first [ArtworkSeed.Loading] fall back to
 * the root scheme. While a new seed loads the previous one stays, so a change reads old → new,
 * never old → root → new. Every change crossfades with the motion scheme's slow effects spring.
 */
@Composable
fun ArtworkTheme(
    seed: ArtworkSeed,
    style: ArtworkSchemeStyle = ArtworkSchemeStyle.Detail,
    content: @Composable () -> Unit,
) {
    // Plain memory, not state: it only feeds the seed chosen in this same composition.
    val lastSeed = remember { SeedMemory() }
    val effectiveSeed = when (seed) {
        is ArtworkSeed.Available -> seed.color.also { lastSeed.color = it }
        ArtworkSeed.Loading -> lastSeed.color
        ArtworkSeed.None -> null.also { lastSeed.color = null }
    }
    val settings = LocalS2ThemeSettings.current
    val rootScheme = LocalRootColorScheme.current ?: MaterialTheme.colorScheme
    val seededScheme = remember(effectiveSeed, style, settings.isDark, settings.contrast) {
        effectiveSeed?.let { artworkColorScheme(it, settings.isDark, style, settings.contrast) }
    }
    MaterialTheme(
        colorScheme = animateSchemeChange(seededScheme ?: rootScheme),
        content = content,
    )
}

private class SeedMemory {
    var color: Color? = null
}
