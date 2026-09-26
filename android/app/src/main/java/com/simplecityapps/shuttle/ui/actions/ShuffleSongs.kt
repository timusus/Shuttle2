package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class ShuffleSongs @Inject constructor(
    private val playbackOperations: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(songs: List<Song>): Result {
        var invokeResult: Result = Result.Success
        playbackOperations.shuffle(songs) { result ->
            result
                .onSuccess { playbackOperations.play() }
                .onFailure { error -> invokeResult = Result.Failure(error.message) }
        }
        return invokeResult
    }
}
