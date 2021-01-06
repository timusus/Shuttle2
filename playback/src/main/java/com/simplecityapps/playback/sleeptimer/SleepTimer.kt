package com.simplecityapps.playback.sleeptimer

import android.os.Handler
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import timber.log.Timber
import java.util.*
import kotlin.math.max

class SleepTimer(
    private val playbackManager: PlaybackManager,
    private val playbackWatcher: PlaybackWatcher
) : PlaybackWatcherCallback {

    private var handler: Handler? = null

    private var playToEnd: Boolean = false

    private var startTime: Date? = null

    private var delay: Long = 0L


    /**
     * Start the sleep timer.
     *
     * @param delay time in milliseconds until the sleep timer should sleep.
     * @param playToEnd whether to wait until the current track ends before pausing playback.
     */
    fun startTimer(delay: Long, playToEnd: Boolean) {

        Timber.v("startTimer() called.. Delay: ${delay}ms")

        startTime = Date()
        this.delay = delay

        this.playToEnd = playToEnd

        handler?.removeCallbacksAndMessages(null)
        handler = Handler()
        handler?.postDelayed({
            if (playToEnd) {
                playbackWatcher.addCallback(this)
            } else {
                sleep()
            }
        }, delay)
    }

    /**
     * Cancels the sleep timer
     */
    fun stopTimer() {
        Timber.v("stopTimer() called")
        handler?.removeCallbacksAndMessages(null)
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
            return max(0L, delay - (Date().time - startTime.time))
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