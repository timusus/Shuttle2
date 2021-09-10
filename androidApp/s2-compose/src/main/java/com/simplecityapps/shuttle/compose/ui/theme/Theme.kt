package com.simplecityapps.shuttle.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = AppColors.primary,
    onPrimary = Color.White,
    secondary = AppColors.secondary,
    onSecondary = Color.White,
    background = AppColors.backgroundDark,
    onBackground = AppColors.onBackgroundDark,
    surface = AppColors.surfaceDark
)

private val LightColorPalette = lightColors(
    primary = AppColors.primary,
    onPrimary = Color.White,
    secondary = AppColors.secondary,
    onSecondary = Color.White,
    background = AppColors.background,
    onBackground = AppColors.onBackground,
    surface = AppColors.surface
)

@Composable
fun S2androidTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}