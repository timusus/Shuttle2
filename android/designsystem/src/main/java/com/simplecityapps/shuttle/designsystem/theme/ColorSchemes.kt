package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.toHct

/**
 * How an artwork seed is turned into a scheme. Detail screens keep the seed to the accent roles;
 * the player lets the seed hue carry into the containers.
 */
enum class ArtworkSchemeStyle(internal val paletteStyle: PaletteStyle) {
    Detail(PaletteStyle.TonalSpot),
    Player(PaletteStyle.Content),
}

/**
 * Seeds below this HCT chroma are too grey to carry a hue, so they fall back to the root accent
 * scheme rather than produce a grey one.
 *
 * Provisional: the value still has to be picked on the catalogue's low-chroma seed.
 */
const val MIN_SEED_CHROMA = 5.0

/** Whether [seed] has enough chroma to generate a scheme from. */
fun isUsableSeed(seed: Color): Boolean = seed.toHct().chroma >= MIN_SEED_CHROMA

/**
 * Generates a scheme from [seed] with the 2025 spec, so root accent and artwork schemes share one
 * role structure.
 */
fun seedColorScheme(
    seed: Color,
    isDark: Boolean,
    style: PaletteStyle = PaletteStyle.TonalSpot,
    contrast: S2Contrast = S2Contrast.Default,
): ColorScheme = dynamicColorScheme(
    seedColor = seed,
    isDark = isDark,
    style = style,
    contrastLevel = contrast.materialKolor.value,
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
)

/** The root scheme for a user-picked [accent]. */
fun accentColorScheme(
    accent: S2Accent,
    isDark: Boolean,
    contrast: S2Contrast = S2Contrast.Default,
): ColorScheme = seedColorScheme(accent.seed, isDark, PaletteStyle.TonalSpot, contrast)

/**
 * The scheme for an artwork [seed], or null when the seed is too grey to use and the caller
 * should fall back to the root accent scheme.
 */
fun artworkColorScheme(
    seed: Color,
    isDark: Boolean,
    style: ArtworkSchemeStyle = ArtworkSchemeStyle.Detail,
    contrast: S2Contrast = S2Contrast.Default,
): ColorScheme? = if (isUsableSeed(seed)) seedColorScheme(seed, isDark, style.paletteStyle, contrast) else null
