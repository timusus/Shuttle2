package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

@Inject
class ShuffleAlbums(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(songs: List<Song>): Result {
        val shuffled = songs
            .groupBy { it.albumGroupKey }
            .entries
            .shuffled()
            .flatMap { it.value }

        if (!queueOperations.setQueue(shuffled)) {
            return Result.Failure(null)
        }
        return suspendCancellableCoroutine { cont ->
            playbackOperations.load(playWhenReady = true) { result ->
                result.onSuccess { cont.resume(Result.Success) }
                result.onFailure { error ->
                    // A later load replaced this one: the play it was for was dropped, not failed.
                    if (error is CancellationException) cont.cancel(error) else cont.resume(Result.Failure(error.message))
                }
            }
        }
    }
}
