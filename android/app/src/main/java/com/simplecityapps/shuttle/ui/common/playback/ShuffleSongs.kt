package com.simplecityapps.shuttle.ui.common.playback

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class ShuffleSongs @Inject constructor(
    private val playbackManager: PlaybackOperations,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(songs: List<Song>): Result {
        var invokeResult: Result = Result.Success
        playbackManager.shuffle(songs) { result ->
            result
                .onSuccess { playbackManager.play() }
                .onFailure { error -> invokeResult = Result.Failure(error.message) }
        }
        return invokeResult
    }
}
