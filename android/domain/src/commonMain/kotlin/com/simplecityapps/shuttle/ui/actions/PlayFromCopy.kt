package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * Plays one copy of a merged song now, keeping the rest of the queue. The queued item is the chosen copy carrying the
 * song's copies with it first, so a later fallback still sees the others.
 */
@Inject
class PlayFromCopy(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
    private val playSongs: PlaySongs,
) {
    suspend operator fun invoke(song: Song, copy: Song): PlaySongs.Result {
        val chosen = copy.copy(copies = listOf(copy) + song.allCopies.filter { it.id != copy.id })
        if (queueOperations.queueStateFlow.value.currentPosition == null) return playSongs(listOf(chosen))
        playbackOperations.playNext(listOf(chosen))
        playbackOperations.skipToNext(ignoreRepeat = true) { playbackOperations.play() }
        return PlaySongs.Result.Success
    }
}
