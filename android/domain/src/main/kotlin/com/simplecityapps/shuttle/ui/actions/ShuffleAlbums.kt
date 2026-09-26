package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ShuffleAlbums @Inject constructor(
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
            playbackOperations.load { result ->
                result.onSuccess {
                    playbackOperations.play()
                    cont.resume(Result.Success)
                }
                result.onFailure { error ->
                    cont.resume(Result.Failure(error.message))
                }
            }
        }
    }
}
