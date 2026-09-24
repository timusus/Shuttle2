package com.simplecityapps.playback.engine

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player

/**
 * The thread [player] must be called on: its application looper, the main thread. [PlaybackManager][com.simplecityapps.playback.PlaybackManager]
 * and [QueueManager][com.simplecityapps.playback.queue.QueueManager] follow the same rule: a call that changes playback
 * or the queue runs on it, straight away if made there, else posted to it; a read made off it returns the last
 * published state instead of asking the player.
 */
class PlayerThread(player: Player) {
    private val looper: Looper = player.applicationLooper

    private val handler = Handler(looper)

    /** Whether the caller is on the player's thread. */
    val isCurrent: Boolean
        get() = Looper.myLooper() == looper

    /** Runs [block] on the player's thread: now if already on it, else posted to it. */
    fun run(block: () -> Unit) {
        if (isCurrent) block() else handler.post(block)
    }
}
