package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The accents a user can pick for the root scheme. Each one seeds a MaterialKolor scheme (see
 * [accentColorScheme]); the seeds match the accents the legacy theme offered, except Shuttle blue's,
 * which is a more saturated azure than the legacy 0xFF0492EA (#496).
 */
enum class S2Accent(val seed: Color) {
    Default(Color(0xFF0088FF)),
    Orange(Color(0xFFF44336)),
    Cyan(Color(0xFF00AFAF)),
    Purple(Color(0xFF8B0F8E)),
    Green(Color(0xFF4CAF50)),
    Amber(Color(0xFFFF9800)),
}
