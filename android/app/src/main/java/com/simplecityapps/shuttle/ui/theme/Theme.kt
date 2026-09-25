package com.simplecityapps.shuttle.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.ThemeMode

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

@Composable
fun AppTheme(
    dynamicColor: Boolean = false,
    theme: ThemeMode = ThemeMode.DayNight,
    accent: Accent = Accent.Default,
    content: @Composable () -> Unit
) {
    val isDark = when (theme) {
        ThemeMode.DayNight -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (accent) {
            Accent.Default -> ShuttleTheme.getColorScheme(isDark)
            Accent.Orange -> OrangeTheme.getColorScheme(isDark)
            Accent.Cyan -> CyanTheme.getColorScheme(isDark)
            Accent.Purple -> PurpleTheme.getColorScheme(isDark)
            Accent.Green -> GreenTheme.getColorScheme(isDark)
            Accent.Amber -> AmberTheme.getColorScheme(isDark)
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
