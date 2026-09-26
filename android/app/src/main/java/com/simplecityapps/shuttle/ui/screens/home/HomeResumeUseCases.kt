package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The queue Home's resume hero offers to pick up (#490): its current [song], the time left in it, and whether it's playing. */
data class ResumeQueue(
    val song: Song,
    val songs: List<Song>,
    val timeLeftMs: Long,
    val playing: Boolean,
)

/**
 * The current queue as Home's resume hero shows it, or null with no queue. The time left is in whole seconds, so a
 * progress tick only re-emits when the hero's text would change; before the first tick it counts from where the song
 * was left, as the player does.
 */
class ObserveResumeQueue @Inject constructor(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
) {
    operator fun invoke(): Flow<ResumeQueue?> = combine(
        queueOperations.queueStateFlow,
        playbackOperations.progressFlow,
        playbackOperations.playbackStateFlow,
    ) { queue, progress, state ->
        queue.currentItem?.song?.let { song ->
            val position = progress?.position ?: song.playbackPosition
            ResumeQueue(
                song = song,
                songs = queue.items.map { it.song },
                timeLeftMs = (song.duration - position).coerceAtLeast(0) / 1000 * 1000L,
                playing = state == PlaybackState.Playing,
            )
        }
    }.distinctUntilChanged()
}

/** Plays the current queue if it's paused, or pauses it. */
class TogglePlayback @Inject constructor(
    private val playbackOperations: PlaybackOperations,
) {
    operator fun invoke() = playbackOperations.togglePlayback()
}
