package com.simplecityapps.playback.exoplayer

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player

/**
 * The player settings [PlaybackManager] asks [ExoPlayerPlayback] for, remembered rather than applied
 * once, because the player is released whenever playback switches to another [Playback] (Chromecast,
 * for example) and rebuilt on the next load. A rebuilt player starts from ExoPlayer's defaults - its
 * own audio session id, no repeat - so [applyTo] hands it everything it must keep.
 *
 * Volume is deliberately absent: it only carries the audio focus duck, and a rebuilt player starts at
 * full volume. Re-applying it would leave local audio ducked after a Cast round trip, since Cast
 * disables the audio focus helper and the focus gain that would restore the volume never arrives.
 *
 * Playback speed is deliberately absent too: it only carries the expired-trial speed ramp, and a
 * rebuilt player has never picked it up. That ramp is being replaced (#232).
 */
internal data class PlayerSettings(
    /**
     * The audio session id to render on, or [C.AUDIO_SESSION_ID_UNSET] to let the player allocate
     * one. A rebuilt player allocating its own would leave system and OEM audio effects attached to
     * a session that no longer exists.
     */
    val audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET,
    /** One of ExoPlayer's `Player.REPEAT_MODE_*` constants. */
    val repeatMode: Int = Player.REPEAT_MODE_OFF
) {
    fun applyTo(player: AudioPlayer) {
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            player.audioSessionId = audioSessionId
        }
        player.repeatMode = repeatMode
    }
}
