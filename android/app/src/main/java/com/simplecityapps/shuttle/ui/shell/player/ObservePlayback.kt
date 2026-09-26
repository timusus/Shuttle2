package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/** The queue as the active shuffle mode presents it; a StateFlow, so its latest snapshot can be read at any time. */
class ObserveQueue @Inject constructor(
    private val queueOperations: QueueOperations,
) {
    operator fun invoke(): StateFlow<QueueState> = queueOperations.queueStateFlow
}

/** How playback is going: whether it plays, its shuffle and repeat modes, and its speed. */
data class PlaybackStatus(
    val state: PlaybackState,
    val shuffleMode: ShuffleMode,
    val repeatMode: RepeatMode,
    /** 1 being normal. */
    val speed: Float,
)

/** The [PlaybackStatus], each time any part of it changes. */
class ObservePlayback @Inject constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
) {
    operator fun invoke(): Flow<PlaybackStatus> = combine(
        playbackOperations.playbackStateFlow,
        queueOperations.shuffleModeFlow,
        queueOperations.repeatModeFlow,
        playbackOperations.playbackSpeedFlow,
        ::PlaybackStatus,
    )
}

/** Playback's progress through the current song, ticking while it plays; null until the first. */
class ObserveProgress @Inject constructor(
    private val playbackOperations: PlaybackOperations,
) {
    operator fun invoke(): Flow<PlaybackProgress?> = playbackOperations.progressFlow
}
