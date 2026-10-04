package com.simplecityapps.shuttle.scrobbling.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.simplecityapps.shuttle.di.WorkerInstanceFactory
import com.simplecityapps.shuttle.di.WorkerKey
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmClient
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmError
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmResult
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding

/**
 * Drains the Last.fm queue, oldest first, [ScrobbleQueue.BATCH_SIZE] at a time (#503 slice 2). A batch that
 * Last.fm accepts is deleted whether each scrobble was accepted or permanently ignored - both are done with.
 * A transient failure (HTTP failure, or error 11/16) keeps the whole batch queued and asks WorkManager to
 * retry with backoff. An invalid session (error 9) signs the user out and stops without retrying forever,
 * leaving the queue intact (a different account signing in clears it, see [ScrobbleQueue.clear]). A bad or suspended
 * API key or signature ([LastFmError.HOLD]) says nothing about the scrobbles themselves, so the queue is held
 * and the run ends without a retry. Any other error is one Last.fm won't accept for this batch however often
 * it is sent, so the batch is dropped rather than blocking the queue until it ages out. Runs before any of that: entries older than [ScrobbleQueue.MAX_AGE] are dropped, since Last.fm
 * rejects their timestamp regardless.
 */
class ScrobbleFlushWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scrobbleDao: ScrobbleDao,
    private val lastFmClient: LastFmClient,
    private val lastFmSessionStore: LastFmSessionStore
) : CoroutineWorker(appContext, workerParams) {
    @WorkerKey(ScrobbleFlushWorker::class)
    @ContributesIntoMap(AppScope::class, binding = binding<WorkerInstanceFactory<*>>())
    @AssistedFactory
    interface Factory : WorkerInstanceFactory<ScrobbleFlushWorker>

    override suspend fun doWork(): Result {
        val cutoffEpochSec = (System.currentTimeMillis() - ScrobbleQueue.MAX_AGE) / 1000
        scrobbleDao.deleteOlderThan(cutoffEpochSec)

        val sessionKey = lastFmSessionStore.session.value?.key ?: return Result.success()

        while (true) {
            val batch = scrobbleDao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, ScrobbleQueue.BATCH_SIZE)
            if (batch.isEmpty()) return Result.success()

            val outcome = sendBatch(batch, sessionKey)
            when (outcome) {
                Outcome.Retry -> return Result.retry()

                Outcome.SignedOut -> {
                    lastFmSessionStore.signOut()
                    return Result.failure()
                }

                Outcome.Hold -> return Result.failure()

                Outcome.Cleared -> {
                    scrobbleDao.deleteByIds(batch.map { it.id })
                    if (batch.size < ScrobbleQueue.BATCH_SIZE) return Result.success()
                }
            }
        }
    }

    /** [Cleared]: the batch is done with, accepted or ignored - its rows are deleted either way. [Hold]: keep it, stop. */
    private enum class Outcome { Cleared, Retry, SignedOut, Hold }

    private suspend fun sendBatch(
        batch: List<QueuedScrobbleEntity>,
        sessionKey: String
    ): Outcome = when (val result = lastFmClient.scrobble(batch, sessionKey)) {
        is LastFmResult.Success -> Outcome.Cleared

        is LastFmResult.Error -> when (result.code) {
            LastFmError.INVALID_SESSION -> Outcome.SignedOut
            in LastFmError.RETRYABLE -> Outcome.Retry
            in LastFmError.HOLD -> Outcome.Hold
            else -> Outcome.Cleared
        }

        LastFmResult.Unreachable -> Outcome.Retry
    }
}
