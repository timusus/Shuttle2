package com.simplecityapps.shuttle.ui

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ThemeMode

class ThemeManager(
    val appearanceSettings: AppearanceSettings
) {
    /**
     * The XML theme only styles the window (background, system bars) and the dialog fragments shown over the shell;
     * content colours come from the Compose theme, including the accent. It no longer varies by accent.
     */
    fun setTheme(context: Context) {
        val theme = appearanceSettings.theme.value
        val extraDark = appearanceSettings.pureBlack.value

        @StyleRes
        val themeRes =
            when (theme) {
                ThemeMode.DayNight -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark else R.style.AppTheme_DayNight
                ThemeMode.Light -> R.style.AppTheme_Light
                ThemeMode.Dark -> if (extraDark) R.style.AppTheme_Dark_ExtraDark else R.style.AppTheme_Dark
            }

        context.setTheme(themeRes)
    }

    fun setDayNightMode() {
        AppCompatDelegate.setDefaultNightMode(getDayNightMode())
    }

    private fun getDayNightMode(): Int = when (appearanceSettings.theme.value) {
        ThemeMode.DayNight -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
    }
}
