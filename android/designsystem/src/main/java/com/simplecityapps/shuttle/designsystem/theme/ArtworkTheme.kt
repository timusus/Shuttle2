package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed

/**
 * Nests an artwork-seeded scheme under [S2Theme] for the player and artwork detail screens.
 *
 * A seed too grey to carry a hue, [ArtworkSeed.None], or a first [ArtworkSeed.Loading] fall back to
 * the root scheme. While a new seed loads the previous one stays, so a change reads old → new,
 * never old → root → new. Every change crossfades with the motion scheme's slow effects spring.
 *
 * An expressive theme, not a plain `MaterialTheme`: only the scheme is replaced, motion, shapes
 * and type pass straight through, so seeded screens keep the expressive components and motion.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    MaterialExpressiveTheme(
        colorScheme = animateSchemeChange(seededScheme ?: rootScheme),
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

private class SeedMemory {
    var color: Color? = null
}
