package com.simplecityapps.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.engine.SongUriResolver.Companion.isDirect

/**
 * Sets [localPlayer]'s wake mode for each item that becomes current: a stream keeps the Wi-Fi awake as well as the
 * CPU, a local file only the CPU. The player keeps one wake mode for its whole playlist, so it's set per item here.
 */
class WakeModeUpdater(private val localPlayer: ExoPlayer) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        mediaItem?.let { localPlayer.setWakeMode(wakeModeFor(it)) }
    }

    companion object {
        fun wakeModeFor(item: MediaItem): Int {
            val streams = item.localConfiguration?.uri?.let { !it.isDirect() || it.scheme == "http" || it.scheme == "https" } == true
            return if (streams) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL
        }
    }
}
