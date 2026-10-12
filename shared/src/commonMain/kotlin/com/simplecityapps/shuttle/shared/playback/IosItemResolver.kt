package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.logging.Logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The streams of the play order the [IosAudioPlayer] plays, resolved when it opens an item, as
 * Android's `SongUriResolver` resolves a song's URI when Media3 opens it. Each queue entry has one resolution, shared by
 * the pre-warm and every open, under one play id; the current item and the two after it are pre-warmed, so the player's
 * open at the gapless join rarely waits on a server. Entries that leave the order are cancelled and their plays ended.
 *
 * Main thread only, [scope] included; the player's decode thread reaches [resolve] by posting to main.
 */
class IosItemResolver internal constructor(
    private val resolver: IosStreamResolver,
    private val scope: CoroutineScope
) {
    private class Entry(
        val item: QueueItem,
        val playId: String,
        val stream: Deferred<Result<IosStream>>
    )

    private val entries = mutableMapOf<Long, Entry>()

    /** The order last [update]d to, by uid: what [resolve] may open. */
    private var order = emptyMap<Long, QueueItem>()

    private val logger = Logger.tagged("IosPlayerController")

    /**
     * [item]'s play id, starting its resolution if it has none. [retryFailed]: a deliberate load asks again after a
     * failure, and a refusal opens the paywall if [playRequested]; a pre-warm keeps the failure until then.
     */
    @OptIn(ExperimentalUuidApi::class)
    internal fun playId(
        item: QueueItem,
        playRequested: Boolean = false,
        retryFailed: Boolean = false
    ): String {
        val existing = entries[item.uid]?.takeIf { it.item.song.id == item.song.id }
        if (existing != null && !(retryFailed && existing.failure() != null)) return existing.playId
        entries.remove(item.uid)?.let(::drop)
        val playId = Uuid.random().toString()
        val stream = scope.async(start = CoroutineStart.UNDISPATCHED) {
            try {
                val stream = resolver.resolve(item.song, 0, playRequested = playRequested, playId = playId)
                // Dropped while the server set it up: the end sent at the drop may have come before the play existed.
                if (!isActive) endPlay(item, playId)
                Result.success(stream)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to resolve the stream for ${item.song.path}" }
                Result.failure(e)
            }
        }
        entries[item.uid] = Entry(item, playId, stream)
        return playId
    }

    /** Forgets every entry but [order]'s, then pre-warms [currentIndex] and the two items after it. */
    internal fun update(
        order: List<QueueItem>,
        currentIndex: Int
    ) {
        this.order = order.associateBy { it.uid }
        (entries.keys - this.order.keys).forEach { uid -> entries.remove(uid)?.let(::drop) }
        order.drop(currentIndex).take(PREWARM).forEach { playId(it) }
    }

    /**
     * Hands [onResult] entry [uid]'s stream, or why it has none, on the main thread once resolved; one not pre-warmed
     * starts resolving now. An item no longer in the order fails.
     */
    fun resolve(
        uid: Long,
        onResult: (IosStream?, String?) -> Unit
    ) {
        val item = order[uid] ?: return onResult(null, "song $uid is no longer queued")
        // A refusal made before StoreKit answered isn't the answer: ask again now the player wants the stream.
        playId(item, retryFailed = (failure(uid) as? ServerStreamNotAllowedException)?.undecided == true)
        val entry = entries.getValue(uid)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = try {
                entry.stream.await()
            } catch (e: CancellationException) {
                Result.failure(e)
            }
            onResult(result.getOrNull(), result.exceptionOrNull()?.let { it.message ?: it::class.simpleName })
        }
    }

    /** Why entry [uid]'s stream couldn't be resolved, or null if it was (or is still resolving). */
    internal fun failure(uid: Long): Throwable? = entries[uid]?.failure()

    /** Ends entry [uid]'s play; opening it again resolves it under a new one. */
    internal fun end(uid: Long) {
        entries.remove(uid)?.let(::drop)
    }

    /** Cancels [entry]'s resolution and ends its play, so a server stops what it started for it (#722). */
    private fun drop(entry: Entry) {
        entry.stream.cancel()
        endPlay(entry.item, entry.playId)
    }

    private fun endPlay(
        item: QueueItem,
        playId: String
    ) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Best effort: a provider that fails to end a play must not take playback down with it
            try {
                resolver.endPlay(item.song, playId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Entry.failure(): Throwable? = if (stream.isCompleted && !stream.isCancelled) stream.getCompleted().exceptionOrNull() else null

    private companion object {
        /** The current item and the two after it. */
        const val PREWARM = 3
    }
}
