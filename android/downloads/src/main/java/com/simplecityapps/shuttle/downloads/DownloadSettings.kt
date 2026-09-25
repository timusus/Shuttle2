package com.simplecityapps.shuttle.downloads

import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSettings @Inject constructor(
    store: SettingsStore
) {
    val wifiOnly = store.preference(WifiOnly)

    companion object {
        /** Downloads wait for an unmetered network. On by default, so nothing eats mobile data unasked. */
        val WifiOnly = Setting.boolean("pref_download_wifi_only", true)
    }
}
