package com.simplecityapps.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Invokes a callback every [intervalMs] between [start] and [stop], on [scope]'s dispatcher (Main in
 * production). The first tick is dispatched straight away rather than run inline.
 */
class ProgressTicker(
    private val scope: CoroutineScope,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS
) {
    private var onTick: (() -> Unit)? = null

    private var job: Job? = null

    /**
     * Starts ticking, or just swaps the callback if already ticking, so repeated calls never run
     * more than one tick loop.
     */
    fun start(onTick: () -> Unit) {
        Timber.v("start()")
        this.onTick = onTick
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (isActive) {
                    this@ProgressTicker.onTick?.invoke()
                    delay(intervalMs)
                }
            }
    }

    fun stop() {
        Timber.v("stop()")
        onTick = null
        job?.cancel()
        job = null
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 100L
    }
}
