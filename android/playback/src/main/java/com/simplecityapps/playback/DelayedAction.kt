package com.simplecityapps.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs at most one delayed action at a time in [scope]: scheduling an action cancels the one pending before
 * it. Nothing runs once [cancel] is called or [scope] is cancelled, even an action whose delay has passed but
 * which hasn't been dispatched yet.
 */
internal class DelayedAction(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun schedule(
        delayMs: Long,
        action: () -> Unit
    ) {
        job?.cancel()
        job =
            scope.launch {
                delay(delayMs)
                action()
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
