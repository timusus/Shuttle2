package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.SyncTrigger
import dev.zacsweers.metro.Inject

/**
 * The daily background sync (#771), as Android's `MediaImportWorker` runs it: Swift's `BGAppRefreshTask` awaits [run],
 * and cancels it when the system ends the task's time.
 */
class BackgroundSync @Inject constructor(
    private val mediaImporter: MediaImporter
) {
    suspend fun run() = mediaImporter.sync(SyncTrigger.Periodic)
}
