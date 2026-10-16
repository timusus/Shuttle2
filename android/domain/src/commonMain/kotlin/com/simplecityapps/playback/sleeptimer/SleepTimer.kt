package com.simplecityapps.playback.sleeptimer

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.workRunOf
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pauses playback once a delay has elapsed, or, when playing to the end, at the end of the song playing after it
 * ([PlaybackOperations.pauseAtEndOfItem], so the next song never starts). The countdown runs on [context] in
 * [appCoroutineScope]; stopping or restarting the timer cancels a play-to-end wait, letting playback move on.
 * [startTimerAtEndOfWork] pauses after the last movement of the work playing instead.
 */
class SleepTimer(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
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
                    playbackOperations.pauseAtEndOfItem()
                } else {
                    playbackOperations.pause()
                }
                stopTimer()
            }
    }

    /**
     * Start a timer that pauses once the last movement of the work playing has ended, replacing any timer already
     * running. The work is read from the queue as it plays, so a queue edited or skipped within the work moves the
     * stop with it; a song outside any work stops at its own end, as [startTimer] with `playToEnd` does.
     */
    fun startTimerAtEndOfWork() {
        timerJob?.cancel()

        startTime = elapsedRealtime()
        delay = 0L

        timerJob =
            appCoroutineScope.launch(context) {
                queueOperations.queueStateFlow
                    .map { state ->
                        val songs = state.items.map { it.song }
                        val position = state.currentPosition
                        val run = position?.let { workRunOf(songs, it) }
                        state.currentItem?.uid to (state.currentItem != null && (run == null || run.last == position))
                    }.distinctUntilChanged()
                    .collectLatest { (_, lastOfWork) ->
                        if (lastOfWork) {
                            playbackOperations.pauseAtEndOfItem()
                            stopTimer()
                        }
                    }
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
}

private fun monotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
