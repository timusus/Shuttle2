package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Builds new queue items in [buildContext], off the main thread, as a long queue takes a while to build, and applies
 * the changes that add them on the main thread, where the player lives.
 */
internal class QueueBuilder(private val buildContext: CoroutineContext) {
    /** Held by each change that adds new items (see [buildThenApply]). Fair, so they take turns in order. */
    private val turns = Mutex()

    /** Builds [songs] as a queue, ready to set. */
    suspend fun build(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int
    ): PreparedQueue = withContext(buildContext) { PreparedQueue.build(songs, shuffleSongs, position) }

    /**
     * Builds what [apply] takes off the main thread, then runs [apply] with it on it. Changes made this way take
     * turns, so they apply in the order they're made, however long each takes to build: songs added while a new queue
     * is being built join it rather than the queue it replaces.
     */
    suspend fun <B, T> buildThenApply(
        build: () -> B,
        apply: (B) -> T
    ): T = turns.withLock {
        val built = withContext(buildContext) { build() }
        withContext(Dispatchers.Main.immediate) { apply(built) }
    }
}
