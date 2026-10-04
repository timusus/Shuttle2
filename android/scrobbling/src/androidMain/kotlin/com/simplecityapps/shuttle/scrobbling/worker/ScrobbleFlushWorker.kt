package com.simplecityapps.shuttle.scrobbling.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.simplecityapps.shuttle.di.WorkerInstanceFactory
import com.simplecityapps.shuttle.di.WorkerKey
import com.simplecityapps.shuttle.scrobbling.flush.FlushResult
import com.simplecityapps.shuttle.scrobbling.flush.ScrobbleFlusher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding

/**
 * Runs [ScrobbleFlusher] as the unique `scrobble_flush` job ([WorkManagerScrobbleFlushScheduler]): WorkManager's
 * unique work is what keeps two flushes from running at once, and its backoff is what retries a transient failure.
 */
class ScrobbleFlushWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val flusher: ScrobbleFlusher
) : CoroutineWorker(appContext, workerParams) {
    @WorkerKey(ScrobbleFlushWorker::class)
    @ContributesIntoMap(AppScope::class, binding = binding<WorkerInstanceFactory<*>>())
    @AssistedFactory
    interface Factory : WorkerInstanceFactory<ScrobbleFlushWorker>

    override suspend fun doWork(): Result = when (flusher.flush()) {
        FlushResult.Done -> Result.success()
        FlushResult.Retry -> Result.retry()
        FlushResult.Held, FlushResult.SignedOut -> Result.failure()
    }
}
