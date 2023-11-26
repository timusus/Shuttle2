package com.simplecityapps.shuttle.ui.screens.settings

import androidx.annotation.StringRes
import com.simplecityapps.shuttle.R

enum class SettingsMenuItem {
    Shuffle,
    SleepTimer,
    Dsp,
    Settings
    ;

    val icon: Int
        get() {
            return when (this) {
                Shuffle -> R.drawable.ic_shuffle_black_24dp
                SleepTimer -> R.drawable.ic_sleep_black_24dp
                Dsp -> R.drawable.ic_equalizer_black_24dp
                Settings -> R.drawable.ic_settings_black_24dp
            }
        }

    val title: Int
        @StringRes
        get() {
            return when (this) {
                SleepTimer -> R.string.settings_menu_sleep_timer
                Shuffle -> R.string.settings_menu_shuffle_all
                Dsp -> R.string.settings_menu_dsp
                Settings -> R.string.settings_menu_settings
            }
        }
}
