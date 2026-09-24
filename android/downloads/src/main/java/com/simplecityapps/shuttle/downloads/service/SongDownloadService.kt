package com.simplecityapps.shuttle.downloads.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.simplecityapps.shuttle.downloads.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

/** Runs song downloads in a foreground service, with a progress notification. */
@UnstableApi
@AndroidEntryPoint
class SongDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.download_notification_channel_name,
        R.string.download_notification_channel_description
    ) {
    // Not named downloadManager: its getter would clash with getDownloadManager() below.
    @Inject
    lateinit var manager: DownloadManager

    @Inject
    lateinit var notificationHelper: DownloadNotificationHelper

    override fun getDownloadManager(): DownloadManager = manager

    // SafeScheduler turns a JobScheduler failure on some OEM ROMs into a log line instead of a crash.
    override fun getScheduler(): Scheduler = SafeScheduler(PlatformScheduler(this, SCHEDULER_JOB_ID))

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int = try {
        super.onStartCommand(intent, flags, startId)
    } catch (e: Exception) {
        // A download service that can't start gives up quietly: DownloadManager keeps its
        // downloads, so they resume the next time the app starts the service.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
            // Android 12+ refuses a foreground start from a boot-originated context, which is
            // where PlatformScheduler resumes pending downloads after a reboot. Expected.
            Timber.w(e, "Download service not allowed to start in the foreground; downloads resume on the next launch")
        } else {
            Timber.e(e, "Download service failed to start")
        }
        stopSelf(startId)
        Service.START_NOT_STICKY
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: @Requirements.RequirementFlags Int
    ): Notification = notificationHelper.buildProgressNotification(
        this,
        R.drawable.ic_download_notification,
        contentIntent(),
        progressMessage(downloads, notMetRequirements),
        downloads,
        notMetRequirements
    )

    /** Null with no downloads, leaving the helper's empty state; the service stops shortly after. */
    private fun progressMessage(
        downloads: List<Download>,
        notMetRequirements: Int
    ): String? = when {
        downloads.isEmpty() -> null
        notMetRequirements and Requirements.NETWORK_UNMETERED != 0 -> getString(R.string.download_notification_waiting_for_wifi)
        notMetRequirements and Requirements.NETWORK != 0 -> getString(R.string.download_notification_waiting_for_network)
        else -> resources.getQuantityString(R.plurals.download_notification_downloading, downloads.size, downloads.size)
    }

    private fun contentIntent(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "song_downloads"

        // Must not collide with the other foreground services' notifications: playback uses 1 and
        // the artwork download 2. Sharing an id confuses the platform's foreground bookkeeping.
        private const val NOTIFICATION_ID = 3

        // WorkManager numbers its JobScheduler jobs up from 0, so stay well clear of that range.
        private const val SCHEDULER_JOB_ID = 0x5332_0D01
    }
}
