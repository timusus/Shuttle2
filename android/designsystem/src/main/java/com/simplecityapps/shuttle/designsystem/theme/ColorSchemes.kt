package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.contrastRatio
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct

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

/**
 * The root scheme for [accent], in the accent's own style. A colour accent uses Fidelity, which keeps the seed's own
 * chroma in the accent roles (tonal spot holds primary to chroma 32 whatever the seed, which read muted, #496) and
 * puts the seed itself in `primaryContainer`, while the neutral surfaces stay near grey. [S2Accent.Neutral] keeps
 * every role near grey.
 */
fun accentColorScheme(
    accent: S2Accent,
    isDark: Boolean,
    contrast: S2Contrast = S2Contrast.Default,
): ColorScheme = seedColorScheme(accent.seed, isDark, accent.style, contrast)

/**
 * The scheme for an artwork [seed], shared by the detail screens and the player, or null when the seed is too grey to
 * use and the caller should fall back to the root accent scheme. Vibrant holds the seed's hue in the accent and
 * container roles at full chroma whatever the seed's tone, so a cover reads as its own colour rather than TonalSpot's
 * muted version of it (#736), and an all-dark cover still gets tinted containers (#409).
 */
fun artworkColorScheme(
    seed: Color,
    isDark: Boolean,
    contrast: S2Contrast = S2Contrast.Default,
): ColorScheme? = if (isUsableSeed(seed)) seedColorScheme(seed, isDark, PaletteStyle.Vibrant, contrast) else null

/**
 * The top of a gradient from the scheme's [ColorScheme.primaryContainer] down to [sheet]: the container's hue and
 * chroma, at [DarkGroundTone] in a dark scheme (Vibrant keeps the container at a mid tone in both themes, which reads
 * neon on a dark sheet), eased toward [sheet] just far enough that onSurface and onSurfaceVariant (4.5:1) and primary
 * (3:1) hold over it. Text, the seek bar and the controls then stay legible wherever the fade has reached, and the
 * status bar's icons, drawn in onSurface's direction, match the colour behind them. A light scheme's mid-tone
 * container does not clear onSurfaceVariant alone.
 */
fun ColorScheme.legibleGroundTop(sheet: Color): Color {
    val container = if (surface.luminance() < 0.5f) primaryContainer.toHct().withTone(DarkGroundTone).toColor() else primaryContainer
    fun legible(ground: Color) = onSurface.contrastRatio(ground) >= 4.5 &&
        onSurfaceVariant.contrastRatio(ground) >= 4.5 &&
        primary.contrastRatio(ground) >= 3.0
    return (0..GroundEaseSteps).firstNotNullOfOrNull { step ->
        lerp(container, sheet, step / GroundEaseSteps.toFloat()).takeIf(::legible)
    } ?: sheet
}

/** M3's dark-scheme container tone. */
private const val DarkGroundTone = 30.0
private const val GroundEaseSteps = 20
