package com.simplecityapps.playback

import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/**
 * Whether a call is ringing or in progress, from the audio mode, and when it ends. Media3 takes the delayed audio focus
 * grant a call gives as focus, so a play during a call would otherwise start at once, over the call (RS-54).
 *
 * Seeing a call end takes the mode-changed callback, API 31+; below that, [awaitCallEnd] can't wait.
 */
class CallMonitor(private val audioManager: AudioManager?) {
    /** The current wait's listener, an [AudioManager.OnModeChangedListener], typed loosely as that's API 31+. */
    private var listener: Any? = null

    /** Any audio mode but normal: a call ringing, in progress, being screened or redirected, or a VoIP call. */
    val isInCall: Boolean
        get() = audioManager != null && audioManager.mode != AudioManager.MODE_NORMAL

    /**
     * Calls [onCallEnded] once, on [executor], when the audio mode returns to normal, replacing any wait already set.
     * Returns false, waiting for nothing, where the platform can't say when a call ends (below API 31).
     */
    fun awaitCallEnd(
        executor: Executor,
        onCallEnded: () -> Unit
    ): Boolean {
        cancel()
        if (audioManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        listen(audioManager, executor, onCallEnded)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun listen(
        audioManager: AudioManager,
        executor: Executor,
        onCallEnded: () -> Unit
    ) {
        val listener =
            object : AudioManager.OnModeChangedListener {
                override fun onModeChanged(mode: Int) {
                    // A change already on its way to the executor when this wait was replaced or cancelled is ignored.
                    if (mode == AudioManager.MODE_NORMAL && this === this@CallMonitor.listener) {
                        cancel()
                        onCallEnded()
                    }
                }
            }
        this.listener = listener
        audioManager.addOnModeChangedListener(executor, listener)
    }

    /** Drops the wait set by [awaitCallEnd], if any. */
    fun cancel() {
        val listener = listener ?: return
        this.listener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager?.removeOnModeChangedListener(listener as AudioManager.OnModeChangedListener)
        }
    }
}
