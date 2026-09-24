package com.simplecityapps.shuttle.downloads

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Applies the Wi-Fi-only preference to downloads as it changes, without a restart.
 *
 * Only a change is pushed. Each [SongDownloadManager.setRequirements] starts the download
 * foreground service, and `DownloadsModule` already builds the `DownloadManager` with the
 * preference as it stands, so re-applying that value on every launch would start a foreground
 * service with nothing to show (a `ForegroundServiceDidNotStartInTimeException` risk on slow
 * devices, and battery churn for nothing).
 */
@Singleton
class DownloadRequirementsManager @Inject constructor(
    private val songDownloadManager: SongDownloadManager,
    private val downloadPreferences: DownloadPreferences
) {
    fun observe(scope: CoroutineScope) {
        var appliedWifiOnly = downloadPreferences.wifiOnly
        downloadPreferences.wifiOnlyFlow
            .onEach { wifiOnly ->
                if (wifiOnly != appliedWifiOnly) {
                    songDownloadManager.setRequirements(wifiOnly)
                    appliedWifiOnly = wifiOnly
                }
            }
            .launchIn(scope)
    }
}
