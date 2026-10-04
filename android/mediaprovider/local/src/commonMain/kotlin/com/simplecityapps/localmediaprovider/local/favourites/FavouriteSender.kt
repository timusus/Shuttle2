package com.simplecityapps.localmediaprovider.local.favourites

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PendingFavouriteData
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drains the `pending_favourites` outbox (#497) to [writer]: every favourite or unfavourite made on a remote-provider
 * song reaches its server, so a heart isn't local-only.
 *
 * [start] watches the outbox, and each time it changes (app start, or a new toggle) sends its rows one at a time, oldest
 * change first, on [scope]. A row is acked once the writer reports success. A row that fails (the server is unreachable,
 * or the session expired) stays queued and doesn't hold up the rows behind it; it isn't sent again by the drains that
 * follow (each ack re-emits the outbox), only by a retry, scheduled with a backoff that starts at [INITIAL_BACKOFF_MS],
 * doubles to [MAX_BACKOFF_MS] while sends keep failing, and starts over once one succeeds or a new toggle is sent.
 * A song that no longer exists, or that no writer handles, is dropped from the outbox rather than retried forever.
 *
 * One collector handles both the outbox and the retry timer, so drains never overlap. The retry delay uses [scope]'s
 * dispatcher, so it runs on virtual time in tests.
 */
private val logger = Logger.tagged("FavouriteSender")

class FavouriteSender(
    private val dao: SongDataDao,
    private val writer: FavouriteWriter,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = TIMEOUT_MS
) {
    private var started = false

    private enum class Trigger { Outbox, Retry }

    /** Rows that failed to send and haven't been replaced since; skipped until the next retry. */
    private val failed = mutableSetOf<PendingFavouriteData>()
    private var backoffMs = INITIAL_BACKOFF_MS
    private var retryJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val retries = Channel<Trigger>(Channel.CONFLATED)
            // The flow is only a trigger: a snapshot it queued while a drain was running is stale by the time it is
            // collected (the drain has acked those rows), so each drain reads the outbox afresh.
            merge(dao.observePendingFavourites().map { Trigger.Outbox }, retries.receiveAsFlow())
                .conflate()
                .collect { trigger ->
                    try {
                        drain(retrying = trigger == Trigger.Retry, retries = retries)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Whatever went wrong (a database error, say), the next trigger or retry tries again.
                        logger.warn(e) { "Draining the favourites outbox failed" }
                    }
                }
        }
    }

    private suspend fun drain(
        retrying: Boolean,
        retries: Channel<Trigger>
    ) {
        val pending = dao.getPendingFavourites()
        failed.retainAll(pending.toSet())
        if (pending.isEmpty()) {
            retryJob?.cancel()
            backoffMs = INITIAL_BACKOFF_MS
            return
        }
        val songs = dao.loadByIds(pending.map { it.songId }).associateBy { it.id }
        var attempted = false
        var succeeded = false
        var sentNew = false
        for (row in pending) {
            if (!retrying && row in failed) continue
            val song = songs[row.songId]
            if (song == null || !writer.handles(song)) {
                // Nothing to send it to; the row would otherwise sit in the outbox for ever.
                dao.ackPendingFavourite(row)
                continue
            }
            attempted = true
            val wasFailed = failed.remove(row)
            if (send(song, row.favourite)) {
                dao.ackPendingFavourite(row)
                succeeded = true
            } else {
                failed += row
                if (!wasFailed) sentNew = true
            }
        }
        if (succeeded || sentNew) {
            backoffMs = INITIAL_BACKOFF_MS
        } else if (retrying) {
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
        if (failed.isNotEmpty() && (attempted || retryJob?.isActive != true)) {
            scheduleRetry(retries)
        } else if (failed.isEmpty()) {
            retryJob?.cancel()
        }
    }

    private fun scheduleRetry(retries: Channel<Trigger>) {
        retryJob?.cancel()
        val wait = backoffMs
        retryJob = scope.launch {
            delay(wait)
            retries.send(Trigger.Retry)
        }
    }

    private suspend fun send(
        song: Song,
        favourite: Boolean
    ): Boolean = try {
        withTimeoutOrNull(timeoutMs) { writer.setFavourite(song, favourite) } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val INITIAL_BACKOFF_MS = 60_000L
        const val MAX_BACKOFF_MS = 3_600_000L
    }
}
