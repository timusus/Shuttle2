package com.simplecityapps.shuttle.scrobbling.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleFlushScheduler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.util.concurrent.TimeUnit

/** (Re-)schedules [ScrobbleFlushWorker] as one unique job, for when the device is online. */
@ContributesBinding(AppScope::class)
class WorkManagerScrobbleFlushScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context
) : ScrobbleFlushScheduler {
    override fun scheduleFlush() {
        val request = OneTimeWorkRequestBuilder<ScrobbleFlushWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "scrobble_flush"
    }
}
