package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.ui.screens.home.HomeItem
import com.simplecityapps.shuttle.ui.screens.home.ResolveHomeItems
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * What the queue was started from (#909), as the item the queue screen says it's "Playing from" and opens: the
 * album, artist, playlist, smart playlist or genre, resolved as Home resolves the contexts it suggests, so it carries
 * the item's current name. Null for a queue started from none of those, or from one the library no longer has.
 *
 * Resolved only when the queue's context changes, not on every change to the queue.
 */
class ObserveQueueSource @Inject constructor(
    private val resolveHomeItems: ResolveHomeItems,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(queue: Flow<QueueState>): Flow<HomeItem?> = queue
        .map { it.playContext }
        .distinctUntilChanged()
        .mapLatest { context -> if (context == PlayContext.None) null else resolveHomeItems(listOf(context))[context] }
}
