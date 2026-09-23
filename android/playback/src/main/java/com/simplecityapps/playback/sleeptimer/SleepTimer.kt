package com.simplecityapps.playback.sleeptimer

import android.os.SystemClock
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Pauses playback once a delay has elapsed, or, when playing to the end, at the first track end after it.
 * The countdown runs on [context] in [appCoroutineScope]; a track end is an event, so the play-to-end wait
 * listens for it on [playbackWatcher].
 */
class SleepTimer(
    private val playbackManager: PlaybackOperations,
    private val playbackWatcher: PlaybackWatcher,
    private val appCoroutineScope: CoroutineScope,
    private val context: CoroutineContext = Dispatchers.Main.immediate,
    /** The clock [timeRemaining] is measured on, in milliseconds. */
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) : PlaybackWatcherCallback {
    private var timerJob: Job? = null

    private var playToEnd: Boolean = false

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
        playbackWatcher.removeCallback(this)

        startTime = elapsedRealtime()
        this.delay = delay

        this.playToEnd = playToEnd

        timerJob =
            appCoroutineScope.launch(context) {
                delay(delay)
                if (playToEnd) {
                    playbackWatcher.addCallback(this@SleepTimer)
                } else {
                    sleep()
                }
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
        playbackWatcher.removeCallback(this)
        startTime = null
        playToEnd = false
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
        playbackManager.pause()
        stopTimer()
    }

    // PlaybackWatcherCallback Implementation

    override fun onTrackEnded(song: Song) {
        Timber.v("onPlaybackComplete, playToEnd: $playToEnd, timeRemaining: ${timeRemaining()}")
        if (playToEnd && timeRemaining() == 0L) {
            sleep()
        }
    }
}
