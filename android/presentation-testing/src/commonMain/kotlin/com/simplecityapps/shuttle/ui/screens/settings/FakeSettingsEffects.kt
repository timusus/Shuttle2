package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.settings.Setting

/** Records what a view model asks the app to do through [SettingsEffects]. */
class FakeSettingsEffects : SettingsEffects {
    /** Every [onSettingChanged] call as (key, value), in order. */
    val changes = mutableListOf<Pair<String, Any?>>()
    var rescans = 0
        private set
    var cacheClears = 0
        private set
    var artworkDownloads = 0
        private set
    var shareResult = ShareDebugLogsResult.Shared

    override fun <T> onSettingChanged(
        setting: Setting<T>,
        value: T
    ) {
        changes += setting.key to value
    }

    override fun rescan() {
        rescans++
    }

    override suspend fun clearArtworkCache() {
        cacheClears++
    }

    override fun downloadAllArtwork() {
        artworkDownloads++
    }

    override suspend fun shareDebugLogs(): ShareDebugLogsResult = shareResult
}
