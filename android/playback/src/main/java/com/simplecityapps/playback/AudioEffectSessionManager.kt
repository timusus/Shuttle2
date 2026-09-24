package com.simplecityapps.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.chromecast.isRemote

/**
 * Advertises the audio session the player renders on, so system and OEM audio effects can attach
 * to it: the player makes the session, but only the app can open an effect control session on it.
 * Once [attach]ed, it follows the local player's session, and closes while playback is on a Cast
 * receiver. At most one effect control session is open at a time.
 *
 * Thread-safe: binds are serialised, and each closes exactly the session the previous bind opened,
 * so no session is closed twice, left open, or closed after a newer one was opened.
 */
class AudioEffectSessionManager(
    private val openSession: (sessionId: Int) -> Unit,
    private val closeSession: (sessionId: Int) -> Unit
) {
    constructor(context: Context) : this(
        openSession = { sessionId -> context.broadcastEffectSession(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, sessionId) },
        closeSession = { sessionId -> context.broadcastEffectSession(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, sessionId) }
    )

    /**
     * The session id the effect control session is currently open on, or null if none is open.
     */
    var sessionId: Int? = null
        @Synchronized get
        private set

    /**
     * Keeps the effect control session on [localPlayer]'s audio session while [player] plays locally,
     * and closed while it plays on a Cast receiver.
     */
    fun attach(
        player: Player,
        localPlayer: ExoPlayer
    ) {
        val follow = { bindTo(if (player.isRemote) C.AUDIO_SESSION_ID_UNSET else localPlayer.audioSessionId) }
        localPlayer.addListener(
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) = follow()
            }
        )
        // Moving between this device and a Cast receiver shows first in whichever event the switch raises first.
        player.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events
                ) = follow()
            }
        )
        follow()
    }

    /**
     * Moves the effect control session to [sessionId]: closes the currently open session (if any)
     * and opens one on [sessionId] if it's a real session. A [sessionId] of zero or less (e.g. a
     * Chromecast playback, which has no local audio session) just closes. No-op if already bound.
     */
    @Synchronized
    fun bindTo(sessionId: Int) {
        val newSessionId = sessionId.takeIf { it > 0 }
        if (newSessionId == this.sessionId) {
            return
        }
        this.sessionId?.let(closeSession)
        this.sessionId = newSessionId
        newSessionId?.let(openSession)
    }
}

private fun Context.broadcastEffectSession(
    action: String,
    sessionId: Int
) {
    sendBroadcast(
        Intent(action).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        }
    )
}
