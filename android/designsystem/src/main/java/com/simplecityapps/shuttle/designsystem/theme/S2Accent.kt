package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle

/**
 * The accents a user can pick for the root scheme. Each one seeds a MaterialKolor scheme in [style] (see
 * [accentColorScheme]); the colour seeds match the accents the legacy theme offered, except Shuttle blue's, which is
 * a more saturated azure than the legacy 0xFF0492EA (#496).
 */
enum class S2Accent(val seed: Color, internal val style: PaletteStyle = PaletteStyle.Fidelity) {
    /**
     * No accent chosen: near-grey chrome with a faint cool cast, so album art on the player and detail screens
     * supplies the colour (#660).
     */
    Neutral(Color(0xFF0088FF), PaletteStyle.Neutral),
    Blue(Color(0xFF0088FF)),
    Orange(Color(0xFFF44336)),
    Cyan(Color(0xFF00AFAF)),
    Purple(Color(0xFF8B0F8E)),
    Green(Color(0xFF4CAF50)),
    Amber(Color(0xFFFF9800)),
}
