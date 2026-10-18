package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackState

/**
 * The published state for the engine's [current] track, as it stands with a load [loadPending] (or not), the engine's
 * last report [engineState] and the user's intent [playWhenReady]. Nothing loaded is paused.
 */
internal fun derivePlaybackState(
    current: IosFeed?,
    loadPending: Boolean,
    engineState: IosAudioPlayerState,
    playWhenReady: Boolean
): PlaybackState {
    if (current == null) return PlaybackState.Paused
    return when {
        loadPending -> PlaybackState.Loading
        !current.ready && !current.failed && engineState == IosAudioPlayerState.Loading -> PlaybackState.Loading
        isBuffering(current, loadPending, engineState, playWhenReady) -> PlaybackState.Loading
        engineState == IosAudioPlayerState.Playing -> PlaybackState.Playing
        else -> PlaybackState.Paused
    }
}

/** An underrun: a ready track the engine reports loading while playback is intended, so the node is waiting on the stream. */
internal fun isBuffering(
    current: IosFeed?,
    loadPending: Boolean,
    engineState: IosAudioPlayerState,
    playWhenReady: Boolean
): Boolean {
    if (current == null) return false
    return !loadPending && current.ready && !current.failed && playWhenReady &&
        engineState == IosAudioPlayerState.Loading
}

internal val PlaybackState.name: String
    get() = this::class.simpleName ?: toString()
