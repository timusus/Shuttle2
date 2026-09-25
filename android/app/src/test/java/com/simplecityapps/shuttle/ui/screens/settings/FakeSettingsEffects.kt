package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.settings.Setting
import java.util.Date

/** Records what [SettingsViewModel] asks the app to do. */
class FakeSettingsEffects : SettingsEffects {
    /** Every [onSettingChanged] call as (key, value), in order. */
    val changes = mutableListOf<Pair<String, Any?>>()
    var lastScan: Date? = null
    var rescans = 0
        private set
    var cacheClears = 0
        private set
    var artworkDownloads = 0
        private set
    var copyResult = CopyDebugLogsResult.Copied

    override fun <T> onSettingChanged(
        setting: Setting<T>,
        value: T
    ) {
        changes += setting.key to value
    }

    override fun lastScanDate(): Date? = lastScan

    override fun rescan() {
        rescans++
    }

    override suspend fun clearArtworkCache() {
        cacheClears++
    }

    override fun downloadAllArtwork() {
        artworkDownloads++
    }

    override suspend fun copyDebugLogs(): CopyDebugLogsResult = copyResult
}
