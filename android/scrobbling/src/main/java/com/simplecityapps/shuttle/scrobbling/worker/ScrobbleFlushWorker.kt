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
 * and the run ends without a retry. Error 6 (invalid parameters) means some row in the batch is malformed: the
 * batch is halved until the offending row is isolated, and only a row that still gets error 6 when sent alone is
 * dropped (only when something else in the run was accepted, and at most [MAX_DROPS_PER_RUN] times), while the rest are scrobbled. Any other error is held like the above and never deletes anything, so a
 * systemic failure (bad auth, deprecated method) cannot empty the queue; only the age purge bounds it.
 * Runs before any of that: entries older than [ScrobbleQueue.MAX_AGE] are dropped, since Last.fm
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

        val run = Run()
        while (true) {
            val batch = scrobbleDao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, ScrobbleQueue.BATCH_SIZE)
            if (batch.isEmpty()) return Result.success()

            val sent = sendBatch(batch, sessionKey, run)
            val outcome = when {
                sent == Outcome.InvalidParams -> isolateInvalidRows(batch, sessionKey, run)

                sent == Outcome.Cleared -> {
                    scrobbleDao.deleteByIds(batch.map { it.id })
                    sent
                }

                else -> sent
            }
            when (outcome) {
                Outcome.Retry -> return Result.retry()

                Outcome.SignedOut -> {
                    lastFmSessionStore.signOut()
                    return Result.failure()
                }

                Outcome.Hold -> return Result.failure()

                Outcome.InvalidParams -> error("isolateInvalidRows resolves InvalidParams")

                Outcome.Cleared -> {
                    // Rows still suspected, with nothing accepted in this run, stay queued: hold rather than refetch them forever.
                    if (run.suspects.isNotEmpty()) return Result.failure()
                    if (batch.size < ScrobbleQueue.BATCH_SIZE) return Result.success()
                }
            }
        }
    }

    /** [Cleared]: the batch is done with, accepted or ignored. [Hold]: keep it, stop. [InvalidParams]: error 6, resolved by [isolateInvalidRows]. */
    private enum class Outcome { Cleared, Retry, SignedOut, Hold, InvalidParams }

    /** What one run has seen: whether Last.fm accepted anything, and the lone error-6 rows waiting for that to corroborate them. */
    private inner class Run {
        var accepted = false
        var drops = 0
        val suspects = mutableListOf<QueuedScrobbleEntity>()

        suspend fun markAccepted() {
            accepted = true
            if (suspects.isEmpty()) return
            scrobbleDao.deleteByIds(suspects.map { it.id })
            drops += suspects.size
            suspects.clear()
        }
    }

    /**
     * Halves [batch] until the row Last.fm rejects as invalid (error 6) is alone, deleting accepted halves as it goes
     * (so a later failure doesn't resend them). A lone rejected row is dropped only once something else in the run was
     * accepted (otherwise error 6 may be systemic), and only up to [MAX_DROPS_PER_RUN] times. Deletes every row it
     * resolves itself, so the caller must not. Any other outcome is returned as-is.
     */
    private suspend fun isolateInvalidRows(
        batch: List<QueuedScrobbleEntity>,
        sessionKey: String,
        run: Run
    ): Outcome {
        if (batch.size == 1) {
            if (run.drops + run.suspects.size >= MAX_DROPS_PER_RUN) return Outcome.Hold
            if (run.accepted) {
                scrobbleDao.deleteByIds(listOf(batch.single().id))
                run.drops++
            } else {
                run.suspects += batch.single()
            }
            return Outcome.Cleared
        }
        val middle = batch.size / 2
        for (half in listOf(batch.subList(0, middle), batch.subList(middle, batch.size))) {
            val outcome = when (val sent = sendBatch(half, sessionKey, run)) {
                Outcome.InvalidParams -> isolateInvalidRows(half, sessionKey, run)

                Outcome.Cleared -> {
                    scrobbleDao.deleteByIds(half.map { it.id })
                    sent
                }

                else -> sent
            }
            if (outcome != Outcome.Cleared) return outcome
        }
        return Outcome.Cleared
    }

    private suspend fun sendBatch(
        batch: List<QueuedScrobbleEntity>,
        sessionKey: String,
        run: Run
    ): Outcome = when (val result = lastFmClient.scrobble(batch, sessionKey)) {
        is LastFmResult.Success -> {
            run.markAccepted()
            Outcome.Cleared
        }

        is LastFmResult.Error -> when (result.code) {
            LastFmError.INVALID_SESSION -> Outcome.SignedOut
            in LastFmError.RETRYABLE -> Outcome.Retry
            in LastFmError.HOLD -> Outcome.Hold
            LastFmError.INVALID_PARAMETERS -> Outcome.InvalidParams
            else -> Outcome.Hold
        }

        LastFmResult.Unreachable -> Outcome.Retry
    }

    internal companion object {
        /** Most lone error-6 rows one run will drop (or suspect) before holding the rest of the queue. */
        const val MAX_DROPS_PER_RUN = 5
    }
}
