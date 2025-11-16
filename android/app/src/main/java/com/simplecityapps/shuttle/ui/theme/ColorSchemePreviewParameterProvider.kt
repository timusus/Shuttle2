package com.simplecityapps.shuttle.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class ColorSchemePreviewParameterProvider : PreviewParameterProvider<ColorScheme> {

    override val values: Sequence<ColorScheme>
        get() = sequenceOf(
            ShuttleTheme.getColorScheme(useDark = false),
            ShuttleTheme.getColorScheme(useDark = true),

            OrangeTheme.getColorScheme(useDark = false),
            OrangeTheme.getColorScheme(useDark = true),

            CyanTheme.getColorScheme(useDark = false),
            CyanTheme.getColorScheme(useDark = true),

            PurpleTheme.getColorScheme(useDark = false),
            PurpleTheme.getColorScheme(useDark = true),

            GreenTheme.getColorScheme(useDark = false),
            GreenTheme.getColorScheme(useDark = true),

            AmberTheme.getColorScheme(useDark = false),
            AmberTheme.getColorScheme(useDark = true)
        )
}
