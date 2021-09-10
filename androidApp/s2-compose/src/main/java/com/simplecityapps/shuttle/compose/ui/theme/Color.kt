package com.simplecityapps.shuttle.compose.ui.theme

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val primary = Color(0xFF0492ea)
    val secondary = Color(0xFF0068A5)
    val background = Color(0xFFF7F7F7)
    val onBackground = Color(0xFF212121)
    val surface = Color(0xFFFFFFFF)

    val backgroundDark = Color(0xFFFFFF)
    val onBackgroundDark = Color(0xFFEFF0F3)
    val surfaceDark = Color(0xFF121212)
}

val MaterialColors: Colors
    @Composable get() = MaterialTheme.colors