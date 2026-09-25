package com.simplecityapps.shuttle.ui

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ThemeMode

class ThemeManager(
    val appearanceSettings: AppearanceSettings
) {
    fun setTheme(context: Context) {
        val theme = appearanceSettings.theme.value
        val accent = appearanceSettings.accent.value
        val extraDark = appearanceSettings.pureBlack.value

        @StyleRes
        val themeRes =
            when (theme) {
                ThemeMode.DayNight -> {
                    when (accent) {
                        Accent.Default -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark else R.style.AppTheme_DayNight
                        Accent.Orange -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Orange else R.style.AppTheme_DayNight_Orange
                        Accent.Cyan -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Cyan else R.style.AppTheme_DayNight_Cyan
                        Accent.Purple -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Purple else R.style.AppTheme_DayNight_Purple
                        Accent.Green -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Green else R.style.AppTheme_DayNight_Green
                        Accent.Amber -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Amber else R.style.AppTheme_DayNight_Amber
                    }
                }

                ThemeMode.Light -> {
                    when (accent) {
                        Accent.Default -> R.style.AppTheme_Light
                        Accent.Orange -> R.style.AppTheme_Light_Orange
                        Accent.Cyan -> R.style.AppTheme_Light_Cyan
                        Accent.Purple -> R.style.AppTheme_Light_Purple
                        Accent.Green -> R.style.AppTheme_Light_Green
                        Accent.Amber -> R.style.AppTheme_Light_Amber
                    }
                }

                ThemeMode.Dark -> {
                    when (accent) {
                        Accent.Default -> if (extraDark) R.style.AppTheme_Dark_ExtraDark else R.style.AppTheme_Dark
                        Accent.Orange -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Orange else R.style.AppTheme_Dark_Orange
                        Accent.Cyan -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Cyan else R.style.AppTheme_Dark_Cyan
                        Accent.Purple -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Purple else R.style.AppTheme_Dark_Purple
                        Accent.Green -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Green else R.style.AppTheme_Dark_Green
                        Accent.Amber -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Amber else R.style.AppTheme_Dark_Amber
                    }
                }
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
