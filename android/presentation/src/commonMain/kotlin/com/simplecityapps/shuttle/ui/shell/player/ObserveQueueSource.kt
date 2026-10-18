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

/** The item the queue plays from, resolved only when its context changes; null if none or gone. */
class ObserveQueueSource @Inject constructor(
    private val resolveHomeItems: ResolveHomeItems,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(queue: Flow<QueueState>): Flow<HomeItem?> = queue
        .map { it.playContext }
        .distinctUntilChanged()
        .mapLatest { context -> if (context == PlayContext.None) null else resolveHomeItems(listOf(context))[context] }
}
