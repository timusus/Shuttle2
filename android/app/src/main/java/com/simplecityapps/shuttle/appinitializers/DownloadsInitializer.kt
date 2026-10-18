package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.downloads.DownloadFallbackObserver
import com.simplecityapps.shuttle.downloads.DownloadRequirementsManager
import com.simplecityapps.shuttle.downloads.UndecodableDownloadMigrator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope

/** Keeps download requirements in step with the Wi-Fi-only preference and repairs failed downloads. */
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
