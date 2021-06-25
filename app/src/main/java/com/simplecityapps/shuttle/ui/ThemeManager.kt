package com.simplecityapps.shuttle.ui

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

class ThemeManager(
    val preferenceManager: GeneralPreferenceManager
) {

    fun setTheme(context: Context) {
        val theme = preferenceManager.themeBase
        val accent = preferenceManager.themeAccent
        val extraDark = preferenceManager.themeExtraDark

        @StyleRes
        val themeRes = when (theme) {
            GeneralPreferenceManager.Theme.DayNight -> {
                when (accent) {
                    GeneralPreferenceManager.Accent.Default -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark else R.style.AppTheme_DayNight
                    GeneralPreferenceManager.Accent.Orange -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Orange else R.style.AppTheme_DayNight_Orange
                    GeneralPreferenceManager.Accent.Cyan -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Cyan else R.style.AppTheme_DayNight_Cyan
                    GeneralPreferenceManager.Accent.Purple -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Purple else R.style.AppTheme_DayNight_Purple
                    GeneralPreferenceManager.Accent.Green -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Green else R.style.AppTheme_DayNight_Green
                    GeneralPreferenceManager.Accent.Amber -> if (extraDark) R.style.AppTheme_DayNight_ExtraDark_Amber else R.style.AppTheme_DayNight_Amber
                }
            }
            GeneralPreferenceManager.Theme.Light -> {
                when (accent) {
                    GeneralPreferenceManager.Accent.Default -> R.style.AppTheme_Light
                    GeneralPreferenceManager.Accent.Orange -> R.style.AppTheme_Light_Orange
                    GeneralPreferenceManager.Accent.Cyan -> R.style.AppTheme_Light_Cyan
                    GeneralPreferenceManager.Accent.Purple -> R.style.AppTheme_Light_Purple
                    GeneralPreferenceManager.Accent.Green -> R.style.AppTheme_Light_Green
                    GeneralPreferenceManager.Accent.Amber -> R.style.AppTheme_Light_Amber
                }
            }
            GeneralPreferenceManager.Theme.Dark -> {
                when (accent) {
                    GeneralPreferenceManager.Accent.Default -> if (extraDark) R.style.AppTheme_Dark_ExtraDark else R.style.AppTheme_Dark
                    GeneralPreferenceManager.Accent.Orange -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Orange else R.style.AppTheme_Dark_Orange
                    GeneralPreferenceManager.Accent.Cyan -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Cyan else R.style.AppTheme_Dark_Cyan
                    GeneralPreferenceManager.Accent.Purple -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Purple else R.style.AppTheme_Dark_Purple
                    GeneralPreferenceManager.Accent.Green -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Green else R.style.AppTheme_Dark_Green
                    GeneralPreferenceManager.Accent.Amber -> if (extraDark) R.style.AppTheme_Dark_ExtraDark_Amber else R.style.AppTheme_Dark_Amber
                }
            }
        }

        context.setTheme(themeRes)
    }

    fun setDayNightMode() {
        AppCompatDelegate.setDefaultNightMode(getDayNightMode())
    }

    private fun getDayNightMode(): Int {
        return when (preferenceManager.themeBase) {
            GeneralPreferenceManager.Theme.DayNight -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            GeneralPreferenceManager.Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
            GeneralPreferenceManager.Theme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}