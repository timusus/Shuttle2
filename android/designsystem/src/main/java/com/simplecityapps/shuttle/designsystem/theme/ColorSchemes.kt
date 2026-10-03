package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct

/**
 * How an artwork seed is turned into a scheme. Detail screens use Vibrant, which holds the seed's hue in the accent
 * roles at full chroma, so Play reads as the cover's colour rather than TonalSpot's muted version of it (#736), while
 * the surfaces stay near neutral under the list; the player lets the seed's own chroma carry into the containers.
 *
 * [minSeedTone] lifts a darker seed to that tone first. The player's Content palette builds its
 * containers at the seed's own tone, so an all-dark cover's seed (tone 5 to 15) would give
 * near-black containers, and a black primary on a light scheme; lifted, it keeps its hue and chroma.
 */
enum class ArtworkSchemeStyle(internal val paletteStyle: PaletteStyle, internal val minSeedTone: Double) {
    Detail(PaletteStyle.Vibrant, minSeedTone = 0.0),

    /** Lifted to 30, M3's dark-scheme container tone. */
    Player(PaletteStyle.Content, minSeedTone = 30.0),
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
 * The scheme for an artwork [seed], or null when the seed is too grey to use and the caller
 * should fall back to the root accent scheme.
 */
fun artworkColorScheme(
    seed: Color,
    isDark: Boolean,
    style: ArtworkSchemeStyle = ArtworkSchemeStyle.Detail,
    contrast: S2Contrast = S2Contrast.Default,
): ColorScheme? = if (isUsableSeed(seed)) seedColorScheme(seed.withMinTone(style.minSeedTone), isDark, style.paletteStyle, contrast) else null

private fun Color.withMinTone(minTone: Double): Color = toHct().let { hct -> if (hct.tone < minTone) hct.withTone(minTone).toColor() else this }
