package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.settings.Setting

/**
 * What a settings change or action does beyond storing a value: the live processors, workers, widgets and
 * system services the legacy preference screens poked. Kept behind an interface so the ViewModels stay free of
 * platform services and are tested against a fake.
 */
interface SettingsEffects {
    /** Called after [setting] has been written with [value]. */
    fun <T> onSettingChanged(
        setting: Setting<T>,
        value: T
    )

    /** Starts a library scan that outlives the screen. */
    fun rescan()

    suspend fun clearArtworkCache()

    fun downloadAllArtwork()

    suspend fun copyDebugLogs(): CopyDebugLogsResult
}

enum class CopyDebugLogsResult { Copied, TooLarge, Empty }
