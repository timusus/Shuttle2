package com.simplecityapps.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps the playback service in the foreground for a play that runs later, maybe from the background: a play [CallHold]
 * holds until a call ends. Android 17 mutes a play from the background with no foreground service, and the service can
 * only be started in the foreground while the user is present, so [acquire] starts it then. Its start
 * ([PlaybackService.ACTION_START], through [ForegroundStarts]) stays in the foreground until [release]: by then the held
 * play has started, and Media3 keeps the foreground, or it was dropped, and Media3 leaves it as for a paused player.
 */
class ForegroundHold(
    /** Starts the playback service in the foreground ([PlaybackService.start]). */
    private val startService: () -> Unit
) {
    private val held = MutableStateFlow(false)

    /** Whether the service is held in the foreground: [acquire]d and not yet [release]d. */
    val isHeld: Boolean
        get() = held.value

    /** Starts the service in the foreground, to stay there until [release]. */
    fun acquire() {
        held.value = true
        startService()
    }

    /** Lets the service leave the foreground. Does nothing if not held. */
    fun release() {
        held.value = false
    }

    /** Returns once nothing holds the service in the foreground. */
    suspend fun awaitRelease() {
        held.first { !it }
    }
}
