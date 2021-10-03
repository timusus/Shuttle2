package com.simplecityapps.shuttle.compose.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import com.simplecityapps.shuttle.compose.R

enum class BottomSettings {
    Shuffle, SleepTimer, Dsp, Settings
}

val BottomSettings.nameResId: Int
    @StringRes
    get() {
        return when (this) {
            BottomSettings.Shuffle -> R.string.settings_menu_shuffle_all
            BottomSettings.SleepTimer -> R.string.settings_menu_sleep_timer
            BottomSettings.Dsp -> R.string.settings_menu_dsp
            BottomSettings.Settings -> R.string.settings_menu_settings
        }
    }

val BottomSettings.icon: ImageVector
    get() {
        return when (this) {
            BottomSettings.Shuffle -> Icons.Outlined.Shuffle
            BottomSettings.SleepTimer -> Icons.Outlined.Nightlight
            BottomSettings.Dsp -> Icons.Outlined.Equalizer
            BottomSettings.Settings -> Icons.Outlined.Settings
        }
    }