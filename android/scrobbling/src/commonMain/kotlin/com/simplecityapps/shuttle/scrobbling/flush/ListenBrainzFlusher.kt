package com.simplecityapps.shuttle.scrobbling.flush

import com.simplecityapps.shuttle.scrobbling.listenbrainz.ListenBrainzClient
import com.simplecityapps.shuttle.scrobbling.listenbrainz.ListenBrainzResult
import com.simplecityapps.shuttle.scrobbling.listenbrainz.ListenBrainzSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.Inject

/**
 * Drains the ListenBrainz queue, oldest first, [ScrobbleQueue.BATCH_SIZE] at a time; [ScrobbleFlusher] runs it
 * beside the Last.fm drain. A rejected token signs the user out and leaves the queue intact; an unreachable or
 * rate-limited ListenBrainz keeps the batch for a retry. A batch refused as malformed (400) is halved until the
 * offending listen is alone, and that one is dropped only once something else in the run was accepted (otherwise
 * the refusal may be systemic) and at most [MAX_DROPS_PER_RUN] times.
 */
class ListenBrainzFlusher
@Inject
constructor(
    private val scrobbleDao: ScrobbleDao,
    private val client: ListenBrainzClient,
    private val sessionStore: ListenBrainzSessionStore
) {
    private var accepted = false
    private var drops = 0

    suspend fun flush(): FlushResult {
        val token = sessionStore.account.value?.token ?: return FlushResult.Done
        accepted = false
        drops = 0
        while (true) {
            val batch = scrobbleDao.oldestBatch(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ, ScrobbleQueue.BATCH_SIZE)
            if (batch.isEmpty()) return FlushResult.Done
            send(batch, token)?.let { return it }
            if (batch.size < ScrobbleQueue.BATCH_SIZE) return FlushResult.Done
        }
    }

    /** Null once [batch] is done with, otherwise the result that ends the run. */
    private suspend fun send(
        batch: List<QueuedScrobbleEntity>,
        token: String
    ): FlushResult? = when (client.submitListens(batch, token)) {
        is ListenBrainzResult.Success -> {
            accepted = true
            scrobbleDao.deleteByIds(batch.map { it.id })
            null
        }

        ListenBrainzResult.InvalidToken -> {
            sessionStore.signOut()
            FlushResult.SignedOut
        }

        ListenBrainzResult.Unreachable -> FlushResult.Retry

        ListenBrainzResult.Rejected -> when {
            batch.size > 1 -> {
                val middle = batch.size / 2
                send(batch.subList(0, middle), token) ?: send(batch.subList(middle, batch.size), token)
            }

            accepted && drops < MAX_DROPS_PER_RUN -> {
                scrobbleDao.deleteByIds(batch.map { it.id })
                drops++
                null
            }

            else -> FlushResult.Held
        }
    }

    internal companion object {
        const val MAX_DROPS_PER_RUN = 5
    }
}
