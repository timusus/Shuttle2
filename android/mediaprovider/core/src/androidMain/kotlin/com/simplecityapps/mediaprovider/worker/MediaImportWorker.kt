package com.simplecityapps.mediaprovider.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.shuttle.di.WorkerInstanceFactory
import com.simplecityapps.shuttle.di.WorkerKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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
        mediaImporter.import()

        return Result.success()
    }

    companion object {
        private const val TAG_MEDIA_IMPORT = "MEDIA_IMPORT"

        private const val INITIAL_DELAY_MINUTES = 15L
        private const val INITIAL_DELAY_JITTER_MINUTES = 16L

        /**
         * Enqueues or removes work, depending on the [ImportFrequency]
         */
        fun updateWork(
            context: Context,
            importFrequency: ImportFrequency
        ) {
            if (importFrequency == ImportFrequency.Never) {
                WorkManager.getInstance(context).cancelAllWorkByTag(TAG_MEDIA_IMPORT)
            } else {
                val request =
                    PeriodicWorkRequestBuilder<MediaImportWorker>(importFrequency.intervalInDays(), TimeUnit.DAYS)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiresBatteryNotLow(true)
                                .setRequiresStorageNotLow(true)
                                .build()
                        )
                        .addTag(TAG_MEDIA_IMPORT)
                        // A short staggered delay instead of a full interval: the idle constraint is gone (it
                        // never fires under Doze), so the first run lands soon after the setting changes without
                        // every install that flips it at once scanning at the same wall-clock time.
                        .setInitialDelay(INITIAL_DELAY_MINUTES + Random.nextLong(INITIAL_DELAY_JITTER_MINUTES), TimeUnit.MINUTES)
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
