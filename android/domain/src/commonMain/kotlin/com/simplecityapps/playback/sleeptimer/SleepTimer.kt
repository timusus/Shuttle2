package com.simplecityapps.playback.sleeptimer

import com.simplecityapps.playback.PlaybackOperations
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pauses playback once a delay has elapsed, or, when playing to the end, at the first track end after it.
 * The countdown runs on [context] in [appCoroutineScope]; the play-to-end wait then collects
 * [PlaybackOperations.trackEndedFlow], which replays nothing, so only a track end after the deadline counts.
 */
class SleepTimer(
    private val playbackOperations: PlaybackOperations,
    private val appCoroutineScope: CoroutineScope,
    private val context: CoroutineContext = Dispatchers.Main.immediate,
    /**
     * The clock [timeRemaining] is measured on, in milliseconds. A monotonic clock by default; Android passes
     * `SystemClock.elapsedRealtime`, which keeps counting through deep sleep.
     */
    private val elapsedRealtime: () -> Long = monotonicMillis()
) {
    private var timerJob: Job? = null

    private var startTime: Long? = null

    private var delay: Long = 0L

    /**
     * Start the sleep timer, replacing any timer already running.
     *
     * @param delay time in milliseconds until the sleep timer should sleep.
     * @param playToEnd whether to wait until the current track ends before pausing playback.
     */
    fun startTimer(
        delay: Long,
        playToEnd: Boolean
    ) {
        timerJob?.cancel()

        startTime = elapsedRealtime()
        this.delay = delay

        timerJob =
            appCoroutineScope.launch(context) {
                delay(delay)
                if (playToEnd) {
                    playbackOperations.trackEndedFlow.first()
                }
                sleep()
            }
    }

    /**
     * Cancels the sleep timer
     */
    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        delay = 0L
        startTime = null
    }

    /**
     * @return the time remaining until sleep, or null if the sleep timer has not been started.
     */
    fun timeRemaining(): Long? {
        startTime?.let { startTime ->
            return max(0L, delay - (elapsedRealtime() - startTime))
        }

        return null
    }

    private fun sleep() {
        playbackOperations.pause()
        stopTimer()
    }
}

private fun monotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
