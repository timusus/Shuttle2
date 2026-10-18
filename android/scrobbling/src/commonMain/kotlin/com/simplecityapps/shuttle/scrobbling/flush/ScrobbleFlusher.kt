package com.simplecityapps.shuttle.scrobbling.flush

import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmClient
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmError
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmResult
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.Inject
import kotlin.time.Clock

/** How a [ScrobbleFlusher.flush] ended, for the platform scheduler that ran it. */
enum class FlushResult {
    /** The queue is empty, or nothing can be sent while signed out. */
    Done,

    /** A transient failure: try again later, with backoff. */
    Retry,

    /** A failure retrying won't fix soon (bad API key, a systemic error, unconfirmed error-6 rows): stop until the next trigger. */
    Held,

    /** A service no longer accepts the session or token (Last.fm error 9, ListenBrainz 401): the user was signed out of it. */
    SignedOut
}

/** Drains the scrobble queues oldest first; a failure holds the batch rather than deleting it, and runs never overlap, so no row is sent twice. */
class ScrobbleFlusher
@Inject
constructor(
    private val scrobbleDao: ScrobbleDao,
    private val lastFmClient: LastFmClient,
    private val lastFmSessionStore: LastFmSessionStore,
    private val listenBrainzFlusher: ListenBrainzFlusher
) {
    /** Drains every signed-in service, each regardless of the other's outcome; the result is the one that most needs the platform scheduler to act. */
    suspend fun flush(): FlushResult {
        val cutoffEpochSec = (Clock.System.now() - ScrobbleQueue.MAX_AGE).epochSeconds
        scrobbleDao.deleteOlderThan(cutoffEpochSec)

        val results = listOf(flushLastFm(), listenBrainzFlusher.flush())
        return listOf(FlushResult.Retry, FlushResult.Held, FlushResult.SignedOut).firstOrNull { it in results } ?: FlushResult.Done
    }

    private suspend fun flushLastFm(): FlushResult {
        val sessionKey = lastFmSessionStore.session.value?.key ?: return FlushResult.Done

        val run = Run()
        while (true) {
            val batch = scrobbleDao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, ScrobbleQueue.BATCH_SIZE)
            if (batch.isEmpty()) return FlushResult.Done

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
                Outcome.Retry -> return FlushResult.Retry

                Outcome.SignedOut -> {
                    lastFmSessionStore.signOut()
                    return FlushResult.SignedOut
                }

                Outcome.Hold -> return FlushResult.Held

                Outcome.InvalidParams -> error("isolateInvalidRows resolves InvalidParams")

                Outcome.Cleared -> {
                    // Rows still suspected, with nothing accepted in this run, stay queued: hold rather than refetch them forever.
                    if (run.suspects.isNotEmpty()) return FlushResult.Held
                    if (batch.size < ScrobbleQueue.BATCH_SIZE) return FlushResult.Done
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
