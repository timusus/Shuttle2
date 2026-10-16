package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.AlbumItem
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.groupWorks
import dev.zacsweers.metro.Inject
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Shuffles an album by work: a work's movements stay together and in order, so a shuffle never lands mid-symphony.
 * The queue keeps album order and the work-wise order is its explicit shuffle order, so the player doesn't reshuffle it.
 */
@Inject
class ShuffleWorks(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(songs: List<Song>, context: PlayContext = PlayContext.None, random: Random = Random): Result {
        val shuffled = groupWorks(songs)
            .shuffled(random)
            .flatMap { item ->
                when (item) {
                    is AlbumItem.Track -> listOf(item.song)
                    is AlbumItem.Work -> item.block.songs
                }
            }
        queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = false)
        if (!queueOperations.setQueue(songs, shuffled, 0, context)) return Result.Failure(null)
        return suspendCancellableCoroutine { cont ->
            playbackOperations.load(playWhenReady = true) { result ->
                result.onSuccess { cont.resume(Result.Success) }
                result.onFailure { error ->
                    if (error is CancellationException) cont.cancel(error) else cont.resume(Result.Failure(error.message))
                }
            }
        }
    }
}
