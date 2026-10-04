package com.simplecityapps.mediaprovider.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.R
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SyncTrigger
import com.simplecityapps.shuttle.di.WorkerInstanceFactory
import com.simplecityapps.shuttle.di.WorkerKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The background sync (#771): brings every source up to date as [MediaImporter.sync] does, at the [ImportFrequency] chosen.
 * Runs as a foreground service with a progress notification, so a long scan isn't cut short by the worker time limit, and
 * is retried with backoff when a source's sync fails.
 */
class MediaImportWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    val mediaImporter: MediaImporter
) : CoroutineWorker(appContext, workerParams) {
    @WorkerKey(MediaImportWorker::class)
    @ContributesIntoMap(AppScope::class, binding = binding<WorkerInstanceFactory<*>>())
    @AssistedFactory
    interface Factory : WorkerInstanceFactory<MediaImportWorker>

    override suspend fun doWork(): Result {
        promoteToForeground()

        // sync reports a source's failure through the import state, not a return value; a periodic sync isn't quiet about it
        val failed = AtomicBoolean(false)
        coroutineScope {
            // Unconfined, so the collector has taken the state the sync starts from (dropped) before the sync publishes anything
            val watcher =
                launch(Dispatchers.Unconfined) {
                    mediaImporter.songImportState.drop(1).collect { state ->
                        if (state is SongImportState.ImportComplete && state.error != null) failed.set(true)
                    }
                }
            mediaImporter.sync(SyncTrigger.Periodic)
            watcher.cancel()
        }

        return if (failed.get()) Result.retry() else Result.success()
    }

    /** The system refuses a foreground start from some background states; the sync then runs as an ordinary worker. */
    private suspend fun promoteToForeground() {
        try {
            setForeground(foregroundInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Couldn't run the media import in the foreground")
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    applicationContext.getString(R.string.media_import_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(applicationContext.getString(R.string.media_import_notification_title))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG_MEDIA_IMPORT = "MEDIA_IMPORT"
        private const val NOTIFICATION_CHANNEL_ID = "media_import"
        private const val NOTIFICATION_ID = 3

        /** The wait before a failed sync's first retry, doubling from there. */
        private const val BACKOFF_DELAY_MINUTES = 15L

        /**
         * Enqueues or removes work, depending on the [ImportFrequency]; [hasRemoteSource] is whether a server is set up, which
         * needs a connection (this device's files alone don't, and shouldn't wait for one).
         */
        fun updateWork(
            context: Context,
            importFrequency: ImportFrequency,
            hasRemoteSource: Boolean
        ) {
            if (importFrequency == ImportFrequency.Never) {
                WorkManager.getInstance(context).cancelAllWorkByTag(TAG_MEDIA_IMPORT)
            } else {
                val request =
                    PeriodicWorkRequestBuilder<MediaImportWorker>(importFrequency.intervalInDays(), TimeUnit.DAYS)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiresBatteryNotLow(true)
                                .setRequiredNetworkType(if (hasRemoteSource) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                                .build()
                        )
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
                        .addTag(TAG_MEDIA_IMPORT)
                        .setInitialDelay(importFrequency.intervalInDays(), TimeUnit.DAYS)
                        .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    // uniqueWorkName =
                    TAG_MEDIA_IMPORT,
                    // existingPeriodicWorkPolicy =
                    ExistingPeriodicWorkPolicy.UPDATE,
                    // periodicWork =
                    request
                )
            }
        }
    }
}
