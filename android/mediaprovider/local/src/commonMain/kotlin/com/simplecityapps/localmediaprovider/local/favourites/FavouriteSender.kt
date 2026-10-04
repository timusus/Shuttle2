package com.simplecityapps.localmediaprovider.local.favourites

import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PendingFavouriteData
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drains the `pending_favourites` outbox (#497) to [writer]: every favourite or unfavourite made on a remote-provider
 * song reaches its server, so a heart isn't local-only.
 *
 * [start] watches the outbox, and each time it changes (app start, or a new toggle) sends its rows one at a time, oldest
 * change first, on [scope]. A row is acked once the writer reports success; one that fails (the server is unreachable,
 * or the session expired) stays queued and is tried again on the next trigger, and doesn't hold up the rows behind it.
 * A song that no longer exists, or that no writer handles, is dropped from the outbox rather than retried forever.
 */
class FavouriteSender(
    private val dao: SongDataDao,
    private val writer: FavouriteWriter,
    private val findSongs: suspend (songIds: List<Long>) -> List<Song>,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = TIMEOUT_MS
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Conflated: toggles made while a drain is running collapse into one more drain afterwards.
            dao.observePendingFavourites().conflate().collect { pending -> drain(pending) }
        }
    }

    private suspend fun drain(pending: List<PendingFavouriteData>) {
        if (pending.isEmpty()) return
        val songs = findSongs(pending.map { it.songId }).associateBy { it.id }
        for (row in pending) {
            val song = songs[row.songId]
            if (song == null || !writer.handles(song)) {
                // Nothing to send it to; the row would otherwise sit in the outbox for ever.
                dao.ackPendingFavourite(row)
                continue
            }
            if (send(song, row.favourite)) dao.ackPendingFavourite(row)
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
    }
}
