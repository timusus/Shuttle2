package com.simplecityapps.playback

import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.simplecityapps.playback.chromecast.isRemote
import timber.log.Timber

/**
 * Follows playback moving between this device and a Cast receiver, as the Cast player around the local one hands it
 * over. The move shows first in whichever event it raises first, so every event checks for it; register this listener
 * on [player] before any other that reads [isRemote] or [isSwitching], so they see the move within the same event.
 */
class CastHandover(
    private val player: Player,
    /** Called with where playback now plays, as soon as a move is seen. */
    private val onSwitch: (remote: Boolean) -> Unit = {}
) : Player.Listener {
    /** Whether [player] plays on a Cast receiver, as of the last player event. */
    var isRemote = player.isRemote
        private set

    /**
     * Whether playback just moved between this device and a Cast receiver, and the player it moved to isn't ready yet.
     * Until it is, what the player reports is the handover, not playback.
     */
    var isSwitching = false
        private set

    private fun check() {
        val remote = player.isRemote
        if (remote == isRemote) return
        isRemote = remote
        Timber.v(if (remote) "Playing on a Cast receiver" else "Playing locally")
        isSwitching = true
        onSwitch(remote)
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int
    ) = check()

    override fun onPlaybackStateChanged(playbackState: Int) {
        check()
        if (playbackState == Player.STATE_READY) isSwitching = false
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int
    ) = check()

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = check()

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) = check()

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) = check()

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = check()

    override fun onPlayerError(error: PlaybackException) {
        check()
        isSwitching = false
    }

    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = check()
}
