package com.simplecityapps.mediaprovider.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.simplecityapps.mediaprovider.MediaImporter
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

/** The background sync (#771): brings every source up to date as [MediaImporter.sync] does, at the [ImportFrequency] chosen. */
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
        mediaImporter.sync(SyncTrigger.Periodic)

        return Result.success()
    }

    companion object {
        private const val TAG_MEDIA_IMPORT = "MEDIA_IMPORT"

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
