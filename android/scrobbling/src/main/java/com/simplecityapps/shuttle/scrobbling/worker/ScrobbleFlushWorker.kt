package com.simplecityapps.shuttle.scrobbling.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.di.WorkerInstanceFactory
import com.simplecityapps.shuttle.di.WorkerKey
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmScrobbleResponse
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSigner
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
 * leaving the queue intact for the next sign-in. Any other top-level error is unrecoverable and drops the
 * batch. Runs before any of that: entries older than [ScrobbleQueue.MAX_AGE] are dropped, since Last.fm
 * rejects their timestamp regardless.
 */
class ScrobbleFlushWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scrobbleDao: ScrobbleDao,
    private val lastFmApi: LastFmApi,
    private val lastFmSessionStore: LastFmSessionStore,
    private val lastFmCredentials: LastFmCredentials
) : CoroutineWorker(appContext, workerParams) {
    @WorkerKey(ScrobbleFlushWorker::class)
    @ContributesIntoMap(AppScope::class, binding = binding<WorkerInstanceFactory<*>>())
    @AssistedFactory
    interface Factory : WorkerInstanceFactory<ScrobbleFlushWorker>

    override suspend fun doWork(): Result {
        val cutoffEpochSec = (System.currentTimeMillis() - ScrobbleQueue.MAX_AGE) / 1000
        scrobbleDao.deleteOlderThan(cutoffEpochSec)

        val sessionKey = lastFmSessionStore.sessionKey ?: return Result.success()

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

                Outcome.Cleared -> {
                    scrobbleDao.deleteByIds(batch.map { it.id })
                    if (batch.size < ScrobbleQueue.BATCH_SIZE) return Result.success()
                }
            }
        }
    }

    /** [Cleared]: the batch is done with, accepted or not - its rows are deleted either way. */
    private enum class Outcome { Cleared, Retry, SignedOut }

    private suspend fun sendBatch(
        batch: List<QueuedScrobbleEntity>,
        sessionKey: String
    ): Outcome {
        val result = lastFmApi.scrobble(buildParams(batch, sessionKey))
        val body = (result as? NetworkResult.Success)?.body ?: return Outcome.Retry

        return when (body.error) {
            null -> Outcome.Cleared
            LastFmScrobbleResponse.ERROR_INVALID_SESSION -> Outcome.SignedOut
            in LastFmScrobbleResponse.RETRYABLE_ERRORS -> Outcome.Retry
            else -> Outcome.Cleared
        }
    }

    private fun buildParams(
        batch: List<QueuedScrobbleEntity>,
        sessionKey: String
    ): Map<String, String> {
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "api_key" to lastFmCredentials.apiKey,
            "sk" to sessionKey
        )
        batch.forEachIndexed { index, entity ->
            params["artist[$index]"] = entity.artist
            params["track[$index]"] = entity.track
            params["timestamp[$index]"] = entity.startedAtEpochSec.toString()
            entity.album?.let { params["album[$index]"] = it }
            entity.albumArtist?.let { params["albumArtist[$index]"] = it }
        }
        val signature = LastFmSigner.sign(params, lastFmCredentials.sharedSecret)
        return params + ("api_sig" to signature) + ("format" to "json")
    }
}
