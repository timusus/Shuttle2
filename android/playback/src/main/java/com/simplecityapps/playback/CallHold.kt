package com.simplecityapps.playback

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.simplecityapps.playback.engine.PlayerThread
import java.util.concurrent.Executor
import timber.log.Timber

/**
 * Holds a play made during a call (ringing or in progress, phone or VoIP) until the call ends, as the player would
 * start playback over it: Media3 takes the delayed audio focus a call gives as focus (RS-54). A queue change drops the
 * hold, as the play was for the queue as it was; so do a pause and a load ([cancel]). While a play is held, the
 * playback service stays in the foreground ([ForegroundHold]).
 */
class CallHold(
    player: Player,
    private val callMonitor: CallMonitor,
    /** Whether playback is on a Cast receiver, where a call doesn't matter. */
    private val isRemote: () -> Boolean,
    /**
     * Keeps the playback service in the foreground while a play is held, from when it's held, while the user is
     * present, until it has started: the held play may run from the background, where Android 17 mutes a play with no
     * foreground service.
     */
    private val foregroundHold: ForegroundHold
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
        val held =
            callMonitor.awaitCallEnd(playerExecutor) {
                play()
                // Released once the play has started, as Media3 then keeps the service in the foreground; not if another
                // call has begun, and held the play again.
                if (!callMonitor.isInCall) foregroundHold.release()
            }
        if (held) {
            Timber.w("play() held until the call ends")
            foregroundHold.acquire()
        } else {
            Timber.w("play() dropped: in a call")
        }
        return true
    }

    /** Drops a held play, if any, letting the service leave the foreground. */
    fun cancel() {
        callMonitor.cancel()
        foregroundHold.release()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int
    ) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) cancel()
    }
}
