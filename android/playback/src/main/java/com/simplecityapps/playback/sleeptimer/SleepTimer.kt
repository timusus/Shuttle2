package com.simplecityapps.playback.sleeptimer

import android.os.SystemClock
import com.simplecityapps.playback.PlaybackOperations
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Pauses playback once a delay has elapsed, or, when playing to the end, at the first track end after it.
 * The countdown runs on [context] in [appCoroutineScope]; the play-to-end wait then collects
 * [PlaybackOperations.trackEndedFlow], which replays nothing, so only a track end after the deadline counts.
 */
class SleepTimer(
    private val playbackOperations: PlaybackOperations,
    private val appCoroutineScope: CoroutineScope,
    private val context: CoroutineContext = Dispatchers.Main.immediate,
    /** The clock [timeRemaining] is measured on, in milliseconds. */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
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
        Timber.v("startTimer() called.. Delay: ${delay}ms")

        timerJob?.cancel()

        startTime = elapsedRealtime()
        this.delay = delay

        timerJob =
            appCoroutineScope.launch(context) {
                delay(delay)
                if (playToEnd) {
                    val song = playbackOperations.trackEndedFlow.first()
                    Timber.v("Track ended after the deadline: ${song.name}")
                }
                sleep()
            }
    }

    /**
     * Cancels the sleep timer
     */
    fun stopTimer() {
        Timber.v("stopTimer() called")
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
        Timber.v("sleep() called")
        playbackOperations.pause()
        stopTimer()
    }
}
