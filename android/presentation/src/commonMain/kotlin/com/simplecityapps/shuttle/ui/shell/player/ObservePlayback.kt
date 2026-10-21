package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.CastDevice
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackStateSource
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** How playback is going: whether it plays, its shuffle and repeat modes, its speed, and the Cast device it plays on. */
data class PlaybackStatus(
    val state: PlaybackState,
    val shuffleMode: ShuffleMode,
    val repeatMode: RepeatMode,
    /** 1 being normal. */
    val speed: Float,
    /** Null while playback plays on this device. */
    val castDevice: CastDevice?,
)

/** The [PlaybackStatus], each time any part of it changes. */
class ObservePlayback @Inject constructor(
    private val playbackStateSource: PlaybackStateSource,
    private val queueOperations: QueueOperations,
) {
    operator fun invoke(): Flow<PlaybackStatus> = combine(
        playbackStateSource.playbackStateFlow,
        queueOperations.shuffleModeFlow,
        queueOperations.repeatModeFlow,
        playbackStateSource.playbackSpeedFlow,
        playbackStateSource.castDeviceFlow,
        ::PlaybackStatus,
    )
}
