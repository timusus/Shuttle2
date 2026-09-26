package com.simplecityapps.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tracing.trace
import timber.log.Timber

/**
 * The player the app plays through: [localPlayer] until Cast is attached, then the Cast player built around it, which
 * hands playback to and from a receiver (see [com.simplecityapps.playback.chromecast.CastQueue]).
 *
 * Cast comes later because setting it up reaches into Play services on the main thread, which the app's start shouldn't
 * wait on, and a process started only in the background for a library scan or a widget update doesn't need at all
 * (see [CastStarter]). The Cast player plays through the same [localPlayer] until a Cast session starts, so moving onto
 * it changes nothing its listeners see: they stay on this player throughout.
 *
 * Main thread only, like the players it wraps.
 */
class AppPlayer(
    localPlayer: ExoPlayer,
    /** Builds the Cast player around the local player and starts Cast; null where Cast isn't available, or in tests. */
    private val castPlayer: (() -> Player?)?
) : ForwardingSimpleBasePlayer(localPlayer) {
    private var castAttached = false

    /** Moves onto the Cast player, once. */
    fun attachCast() {
        if (castAttached) return
        castAttached = true
        trace("S2 attach Cast") {
            try {
                castPlayer?.invoke()?.let(::setPlayer)
            } catch (e: Exception) {
                Timber.w(e, "Failed to set up Cast; playing locally only")
            }
        }
    }
}
