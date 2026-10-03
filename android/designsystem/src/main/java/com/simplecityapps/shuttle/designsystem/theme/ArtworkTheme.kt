package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed

/**
 * Nests an artwork-seeded scheme under [S2Theme] for the player and artwork detail screens.
 *
 * A seed too grey to carry a hue, [ArtworkSeed.None], or a first [ArtworkSeed.Loading] fall back to
 * the root scheme. While a new seed loads the previous one stays, so a change reads old → new,
 * never old → root → new. Every change crossfades with the motion scheme's slow effects spring, and
 * so does [artworkRole]'s switch between root and artwork roles as a seed comes and goes.
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
        is ArtworkSeed.Available -> Color(seed.argb).also { lastSeed.color = it }
        ArtworkSeed.Loading -> lastSeed.color
        ArtworkSeed.None -> null.also { lastSeed.color = null }
    }
    val settings = LocalS2ThemeSettings.current
    val rootScheme = LocalRootColorScheme.current ?: MaterialTheme.colorScheme
    val seededScheme = remember(effectiveSeed, style, settings.isDark, settings.contrast) {
        effectiveSeed?.let { artworkColorScheme(it, settings.isDark, style, settings.contrast) }
    }
    // Medium and High contrast push the containers away from the surface tones, so a container
    // ground would no longer carry surface text at AA; there [artworkRole] keeps the root roles.
    val artworkRoles = seededScheme != null && settings.contrast == S2Contrast.Default
    val weight by animateFloatAsState(if (artworkRoles) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec())
    MaterialTheme(colorScheme = animateSchemeChange(seededScheme ?: rootScheme)) {
        CompositionLocalProvider(LocalArtworkWeight provides weight, content = content)
    }
}

/**
 * How far the nearest [ArtworkTheme] has crossfaded to [artworkRole]'s artwork roles: 1 painting
 * from a seed at Default contrast, 0 on the root scheme, at a raised contrast, or outside any [ArtworkTheme].
 */
private val LocalArtworkWeight = compositionLocalOf { 0f }

/**
 * [artwork] under an [ArtworkTheme] painting from an artwork seed at Default contrast, [root] where it
 * falls back to the root scheme, at a raised contrast, or outside one, crossfading as a seed comes and goes. Lets a surface take a richer
 * role only when the artwork supplies one, so the fallback looks exactly as it did without artwork.
 */
@Composable
@ReadOnlyComposable
fun artworkRole(root: Color, artwork: Color): Color = when (val weight = LocalArtworkWeight.current) {
    0f -> root
    1f -> artwork
    else -> lerp(root, artwork, weight)
}

private class SeedMemory {
    var color: Color? = null
}
