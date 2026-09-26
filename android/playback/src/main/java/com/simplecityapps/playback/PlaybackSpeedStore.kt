package com.simplecityapps.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.simplecityapps.playback.chromecast.isRemote
import com.simplecityapps.shuttle.settings.Preference

/**
 * Keeps the player's speed across restarts in [playbackSpeed]: [restore] puts it back onto a player that starts at
 * normal speed, and every change the player reports is saved. A Cast receiver's speed is its own; the one kept is the
 * local player's.
 */
class PlaybackSpeedStore(
    private val player: Player,
    private val playbackSpeed: Preference<Float>
) : Player.Listener {
    /** Sets the saved speed on [player], unless it's normal speed. Pitch stays put, as [PlaybackOperations.setPlaybackSpeed] sets it. */
    fun restore() {
        playbackSpeed.value.takeIf { it > 0f && it != PlaybackParameters.DEFAULT.speed }?.let { speed ->
            player.playbackParameters = PlaybackParameters(speed)
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        if (!player.isRemote) playbackSpeed.value = playbackParameters.speed
    }
}
