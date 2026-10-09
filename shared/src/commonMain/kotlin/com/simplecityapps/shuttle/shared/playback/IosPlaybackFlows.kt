package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.Play
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** What [IosPlayerController] publishes, written by its collaborators on the main thread. */
internal class IosPlaybackFlows {
    /**
     * Whether the user wants playback to run. This is the value loads, plays, pauses, failures and queue changes
     * actually use — not a copy beside it. Distinct from [playbackState]: a track can be loading either way, and an
     * idle, ended or failed track does not keep the intent.
     */
    val playWhenReadyFlow = MutableStateFlow(false)

    var playWhenReady: Boolean
        get() = playWhenReadyFlow.value
        set(value) {
            playWhenReadyFlow.value = value
        }

    val playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Paused)

    val buffering = MutableStateFlow(false)

    val progress = MutableStateFlow<PlaybackProgress?>(null)

    val playbackSpeed = MutableStateFlow(1f)

    val play = MutableStateFlow<Play?>(null)

    val trackEnded = eventFlow<TrackEnd>()

    /** Playback paused at the end of an item: asked to ([IosEngineFeeder.pausesAtEnd]), or the queue's end. */
    val endOfItemPause = eventFlow<Unit>()

    val pausePosition = eventFlow<SongPosition>()

    val playbackFailure = eventFlow<Song>()

    private companion object {
        /** How many events a flow of them buffers for a collector that hasn't caught up. */
        const val EVENT_BUFFER = 64

        fun <T> eventFlow() = MutableSharedFlow<T>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
}
