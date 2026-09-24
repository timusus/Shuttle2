package com.simplecityapps.shuttle.downloads

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import observeBoolean

class DownloadPreferences @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    /** Downloads wait for an unmetered network. On by default, so nothing eats mobile data unasked. */
    var wifiOnly: Boolean
        get() = sharedPreferences.getBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY)
        set(value) {
            sharedPreferences.edit { putBoolean(KEY_WIFI_ONLY, value) }
        }

    val wifiOnlyFlow: Flow<Boolean>
        get() = sharedPreferences.observeBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY)

    companion object {
        const val KEY_WIFI_ONLY = "pref_download_wifi_only"
        private const val DEFAULT_WIFI_ONLY = true
    }
}
