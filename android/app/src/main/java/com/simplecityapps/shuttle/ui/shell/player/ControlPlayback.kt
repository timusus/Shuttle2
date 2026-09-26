package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What the player's transport, mode and speed controls ask of playback. */
sealed interface PlaybackCommand {
    data object TogglePlayback : PlaybackCommand

    /**
     * Plays once the saved queue has been restored, when nothing is loaded yet. A restore that brings nothing back
     * leaves nothing to play.
     */
    data object PlayWhenRestored : PlaybackCommand

    /** Skips even with repeat-one on. */
    data object SkipToNext : PlaybackCommand

    /** Goes back a song within the current one's first moments, else restarts it. */
    data object SkipToPrevious : PlaybackCommand

    data class SeekTo(val positionMs: Long) : PlaybackCommand

    data object ToggleShuffle : PlaybackCommand

    data object CycleRepeatMode : PlaybackCommand

    /** 1 being normal; the pitch stays the same at any speed. */
    data class SetSpeed(val speed: Float) : PlaybackCommand
}

/** Carries out a [PlaybackCommand]. */
class ControlPlayback @Inject constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
) {
    suspend operator fun invoke(command: PlaybackCommand) {
        when (command) {
            PlaybackCommand.TogglePlayback -> playbackOperations.togglePlayback()

            PlaybackCommand.PlayWhenRestored -> {
                val restored = queueOperations.queueStateFlow.first { queue -> queue.isRestored }
                if (restored.items.isNotEmpty()) playbackOperations.play()
            }

            PlaybackCommand.SkipToNext -> playbackOperations.skipToNext(ignoreRepeat = true)

            PlaybackCommand.SkipToPrevious -> playbackOperations.skipToPrev()

            is PlaybackCommand.SeekTo -> playbackOperations.seekTo(command.positionMs.toInt())

            PlaybackCommand.ToggleShuffle -> queueOperations.toggleShuffleMode()

            PlaybackCommand.CycleRepeatMode -> queueOperations.toggleRepeatMode()

            is PlaybackCommand.SetSpeed -> playbackOperations.setPlaybackSpeed(command.speed)
        }
    }
}
