package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

@Inject
class PlaySongs(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    /** Plays [songs] from [position], as a queue started from [context]. */
    suspend operator fun invoke(songs: List<Song>, position: Int = 0, context: PlayContext = PlayContext.None): Result {
        if (!queueOperations.setQueue(songs, position = position, context = context)) {
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
