package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.settings.DownloadSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AppScope::class)
class SettingsOfflineModeSource @Inject constructor(
    private val downloadSettings: DownloadSettings,
) : OfflineModeSource {
    override fun observe(): Flow<Boolean> = downloadSettings.offlineMode.flow

    override fun set(enabled: Boolean) {
        downloadSettings.offlineMode.value = enabled
    }
}
