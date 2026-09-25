package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import java.util.Date

/** Settings UI states the characterisation tests render. */
object SettingsScenarios {
    val darkPureBlack = SettingsUiState(
        values = mapOf(
            AppearanceSettings.Theme.key to ThemeMode.Dark,
            AppearanceSettings.PureBlack.key to true
        )
    )

    val fileLoggingOff = SettingsUiState(values = mapOf(DebugSettings.FileLogging.key to false))

    val fileLoggingOn = SettingsUiState(values = mapOf(DebugSettings.FileLogging.key to true))

    val scannedWeekly = SettingsUiState(
        values = mapOf(LibrarySettings.RescanFrequency.key to ImportFrequency.Weekly),
        lastScanDate = Date(0)
    )
}
