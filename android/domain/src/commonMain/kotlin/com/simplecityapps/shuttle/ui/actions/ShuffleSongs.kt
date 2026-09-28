package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

@Inject
class ShuffleSongs(
    private val playbackOperations: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    /** Shuffles [songs], as a queue started from [context]. */
    suspend operator fun invoke(songs: List<Song>, context: PlayContext = PlayContext.None): Result {
        var invokeResult: Result = Result.Success
        playbackOperations.shuffle(songs, context) { result ->
            result
                .onSuccess { playbackOperations.play() }
                .onFailure { error -> invokeResult = Result.Failure(error.message) }
        }
        return invokeResult
    }
}
