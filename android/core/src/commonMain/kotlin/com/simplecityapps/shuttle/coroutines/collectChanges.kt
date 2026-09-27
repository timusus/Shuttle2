package com.simplecityapps.shuttle.coroutines

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Collects [flow] in this scope, passing [onChange] each value that differs from the last one handled,
 * with that last one.
 *
 * [baseline] is the snapshot of [flow] the caller has already acted on (or, where it acted on a live read
 * instead, a snapshot taken before that read). The first comparison is against it rather than whatever
 * [flow] holds when collection starts, so a change made between the snapshot and the start of collection
 * is still delivered, and the value [flow] replays on collection isn't handled twice.
 *
 * A StateFlow keeps only its latest value, so values set in quick succession can arrive as one change.
 */
fun <V> CoroutineScope.launchCollectingChanges(
    flow: StateFlow<V>,
    baseline: V,
    context: CoroutineContext = EmptyCoroutineContext,
    onChange: (previous: V, current: V) -> Unit
): Job = launch(context) {
    var previous = baseline
    flow.collect { current ->
        if (current != previous) {
            onChange(previous, current)
            previous = current
        }
    }
}
