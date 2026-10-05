package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.downloads.DownloadFallbackObserver
import com.simplecityapps.shuttle.downloads.DownloadRequirementsManager
import com.simplecityapps.shuttle.downloads.UndecodableDownloadMigrator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Keeps the download requirements in step with the Wi-Fi-only preference, and retries a failed
 * download once with a fallback URL when the server rejects it with 401/403 (#322), for the
 * process's life, and downloads again those saved from a transcode the player can't decode (#936).
 */
class DownloadsInitializer
@Inject
constructor(
    private val downloadRequirementsManager: DownloadRequirementsManager,
    private val downloadFallbackObserver: DownloadFallbackObserver,
    private val undecodableDownloadMigrator: UndecodableDownloadMigrator,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        downloadRequirementsManager.observe(appCoroutineScope)
        downloadFallbackObserver.observe(appCoroutineScope)
        undecodableDownloadMigrator.migrate(appCoroutineScope)
    }
}
