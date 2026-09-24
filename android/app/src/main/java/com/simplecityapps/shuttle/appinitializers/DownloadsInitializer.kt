package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.downloads.DownloadRequirementsManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Keeps the download requirements in step with the Wi-Fi-only preference for the process's life. */
class DownloadsInitializer
@Inject
constructor(
    private val downloadRequirementsManager: DownloadRequirementsManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        downloadRequirementsManager.observe(appCoroutineScope)
    }
}
