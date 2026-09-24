package com.simplecityapps.shuttle.downloads.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import timber.log.Timber

/**
 * Wraps a [Scheduler] so a platform failure to (un)schedule the resume job degrades instead of
 * crashing.
 *
 * [androidx.media3.exoplayer.scheduler.PlatformScheduler] delegates to `JobScheduler`, which on some
 * OEM ROMs, and briefly right after an app update, throws `IllegalArgumentException: No such service
 * ComponentInfo{…}`. That escapes `DownloadService.onStartCommand` and kills the process. A failed
 * schedule only means downloads don't resume in the background until the app is next opened;
 * `DownloadManager` keeps their state, so nothing is lost.
 */
@UnstableApi
class SafeScheduler(private val delegate: Scheduler) : Scheduler {
    override fun schedule(
        requirements: Requirements,
        servicePackage: String,
        serviceAction: String
    ): Boolean = runCatchingScheduler("schedule") {
        delegate.schedule(requirements, servicePackage, serviceAction)
    } ?: false

    override fun cancel(): Boolean = runCatchingScheduler("cancel") {
        delegate.cancel()
    } ?: false

    override fun getSupportedRequirements(requirements: Requirements): Requirements = runCatchingScheduler("getSupportedRequirements") {
        delegate.getSupportedRequirements(requirements)
    } ?: requirements

    private inline fun <T> runCatchingScheduler(
        operation: String,
        block: () -> T
    ): T? = try {
        block()
    } catch (e: Exception) {
        Timber.e(e, "Download scheduler $operation failed")
        null
    }
}
