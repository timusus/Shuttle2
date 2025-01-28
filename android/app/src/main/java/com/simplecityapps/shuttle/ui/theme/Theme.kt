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
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

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
    theme: GeneralPreferenceManager.Theme = GeneralPreferenceManager.Theme.DayNight,
    accent: GeneralPreferenceManager.Accent = GeneralPreferenceManager.Accent.Default,
    extraDark: Boolean = false,
    content: @Composable () -> Unit,
) {

    val isDark = when (theme) {
        GeneralPreferenceManager.Theme.DayNight -> isSystemInDarkTheme()
        GeneralPreferenceManager.Theme.Light -> false
        GeneralPreferenceManager.Theme.Dark -> true
    }

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (accent) {
            GeneralPreferenceManager.Accent.Default -> ShuttleTheme.getColorScheme(isDark)
            GeneralPreferenceManager.Accent.Orange -> OrangeTheme.getColorScheme(isDark)
            GeneralPreferenceManager.Accent.Cyan -> CyanTheme.getColorScheme(isDark)
            GeneralPreferenceManager.Accent.Purple -> PurpleTheme.getColorScheme(isDark)
            GeneralPreferenceManager.Accent.Green -> GreenTheme.getColorScheme(isDark)
            GeneralPreferenceManager.Accent.Amber -> AmberTheme.getColorScheme(isDark)
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

