package com.simplecityapps.shuttle.shared.playback

import kotlinx.coroutines.CancellationException

internal class PendingLoad(
    val completion: (Result<Boolean>) -> Unit,
    val skipUnloadable: Boolean,
    val attempt: Int = 1
)

/** The completion of the last load (or skip), called once its item is ready or nothing could load. */
internal class IosPendingLoads {
    var pending: PendingLoad? = null

    /** Songs skipped in a row because they failed to load. */
    var failures = 0

    /** The uid of the item whose copies a failure has tried, and the song ids it tried; forgotten when the user starts an item, playback moves on or repeats it, or every copy failed. */
    var triedCopies: Pair<Long, Set<Long>>? = null

    /** Makes [load] the pending load. One still pending never had its item ready, and is told it was dropped. */
    fun replace(load: PendingLoad) {
        complete(Result.failure(CancellationException("Replaced by a later load")))
        triedCopies = null
        pending = load
    }

    fun complete(result: Result<Boolean>) {
        val load = pending ?: return
        pending = null
        load.completion(result)
    }
}
