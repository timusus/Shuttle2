package com.simplecityapps.playback

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.simplecityapps.playback.engine.PlayerThread
import java.util.concurrent.Executor
import timber.log.Timber

/**
 * Holds a play made during a call (ringing or in progress, phone or VoIP) until the call ends, as the player would
 * start playback over it: Media3 takes the delayed audio focus a call gives as focus (RS-54). A queue change drops the
 * hold, as the play was for the queue as it was; so do a pause and a load ([cancel]).
 */
class CallHold(
    player: Player,
    private val callMonitor: CallMonitor,
    /** Whether playback is on a Cast receiver, where a call doesn't matter. */
    private val isRemote: () -> Boolean
) : Player.Listener {
    private val playerThread = PlayerThread(player)

    private val playerExecutor = Executor { command -> playerThread.run(command::run) }

    /**
     * Whether [play] is held off by a call: it waits, and runs on the player's thread when the call ends. Below API 31,
     * where the end of a call can't be seen, it's dropped. That includes a play while the call's focus loss holds
     * playback off: setting the player to play again asks for focus again, and the call's delayed grant would start
     * it. A play on a Cast receiver goes ahead.
     */
    fun holds(play: () -> Unit): Boolean {
        if (isRemote() || !callMonitor.isInCall) return false
        if (callMonitor.awaitCallEnd(playerExecutor, play)) {
            Timber.w("play() held until the call ends")
        } else {
            Timber.w("play() dropped: in a call")
        }
        return true
    }

    /** Drops a held play, if any. */
    fun cancel() = callMonitor.cancel()

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int
    ) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) cancel()
    }
}
