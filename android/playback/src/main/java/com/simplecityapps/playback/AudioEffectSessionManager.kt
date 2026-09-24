package com.simplecityapps.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Advertises the audio session the player renders on, so system and OEM audio effects can attach
 * to it. [PlaybackManager] binds it to the player's session when it's created. At most one effect
 * control session is open at a time.
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
