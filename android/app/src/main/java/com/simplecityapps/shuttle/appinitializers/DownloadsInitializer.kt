package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.downloads.DownloadFallbackObserver
import com.simplecityapps.shuttle.downloads.DownloadRequirementsManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Keeps the download requirements in step with the Wi-Fi-only preference, and retries a failed
 * download once with a fallback URL when the server rejects it with 401/403 (#322), for the
 * process's life.
 */
class DownloadsInitializer
@Inject
constructor(
    private val downloadRequirementsManager: DownloadRequirementsManager,
    private val downloadFallbackObserver: DownloadFallbackObserver,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        downloadRequirementsManager.observe(appCoroutineScope)
        downloadFallbackObserver.observe(appCoroutineScope)
    }
}
