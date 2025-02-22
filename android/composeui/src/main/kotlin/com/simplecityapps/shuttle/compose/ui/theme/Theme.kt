package com.simplecityapps.shuttle.compose.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color,
)

@Composable
fun AppTheme(
    dynamicColor: Boolean = false,
    theme: ThemeBase = ThemeBase.DayNight,
    accent: ThemeAccent = ThemeAccent.Default,
    extraDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (theme) {
        ThemeBase.DayNight -> isSystemInDarkTheme()
        ThemeBase.Light -> false
        ThemeBase.Dark -> true
    }

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (accent) {
            ThemeAccent.Default -> ShuttleTheme.getColorScheme(isDark)
            ThemeAccent.Orange -> OrangeTheme.getColorScheme(isDark)
            ThemeAccent.Cyan -> CyanTheme.getColorScheme(isDark)
            ThemeAccent.Purple -> PurpleTheme.getColorScheme(isDark)
            ThemeAccent.Green -> GreenTheme.getColorScheme(isDark)
            ThemeAccent.Amber -> AmberTheme.getColorScheme(isDark)
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

enum class ThemeBase {
    DayNight,
    Light,
    Dark
}

enum class ThemeAccent {
    Default,
    Orange,
    Cyan,
    Purple,
    Green,
    Amber
}
