package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.PlayContext
import dev.zacsweers.metro.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Plays a context from where it was left (#670), as a Jump back in tile's Play does: the player carries on if the queue
 * is still that context's; otherwise the context's songs are queued again from its resume point's song and position,
 * with shuffle on again if it was; a queue that fails leaves the shuffle mode as it was. [Result.StartOver] says to play
 * it from the start instead: it has no resume point, its songs played through, or the point's song is no longer among
 * them.
 */
@Inject
class ResumeContext(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
    private val playHistoryRepository: PlayHistoryRepository,
    private val resolveSongs: ResolveSongs,
) {
    sealed interface Result {
        data object Resumed : Result
        data object StartOver : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(selection: MediaSelection, context: PlayContext): Result {
        if (context == PlayContext.None) return Result.StartOver
        val point = playHistoryRepository.resumePoint(context)
        if (point?.finished == true) return Result.StartOver
        if (queueOperations.playContext == context && queueOperations.getCurrentItem() != null) {
            playbackOperations.play()
            return Result.Resumed
        }
        if (point == null) return Result.StartOver
        val songs = resolveSongs(selection)
        val song = songs.find { it.mediaProvider == point.mediaProvider && it.path == point.songPath } ?: return Result.StartOver

        // The shuffle mode goes first, as the queue's position is into the order it picks; a queue that fails puts it back.
        val shuffleMode = queueOperations.getShuffleMode()
        val queued = if (point.shuffled) {
            // The saved song goes back where it was in the shuffled order, so the track count reads as it did.
            val position = point.track.coerceIn(0, songs.lastIndex)
            val shuffled = (songs - song).shuffled().toMutableList().apply { add(position, song) }
            queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = false)
            queueOperations.setQueue(songs, shuffled, position, context)
        } else {
            queueOperations.setShuffleMode(ShuffleMode.Off, reshuffle = false)
            queueOperations.setQueue(songs, position = songs.indexOf(song), context = context)
        }
        if (!queued) {
            queueOperations.setShuffleMode(shuffleMode, reshuffle = false)
            return Result.Failure(null)
        }
        return suspendCancellableCoroutine { cont ->
            playbackOperations.load(seekPosition = point.positionMs.toInt()) { result ->
                result.onSuccess {
                    playbackOperations.play()
                    cont.resume(Result.Resumed)
                }
                result.onFailure { error ->
                    cont.resume(Result.Failure(error.message))
                }
            }
        }
    }
}
