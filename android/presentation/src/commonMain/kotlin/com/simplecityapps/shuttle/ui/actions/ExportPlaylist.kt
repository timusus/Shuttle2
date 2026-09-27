package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.M3uWriter
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** Writes [songs] as an m3u under [name] to [destination], a platform location string from the route. */
class ExportPlaylist @Inject constructor(
    private val m3uWriter: M3uWriter,
    private val fileWriter: PlaylistFileWriter,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    suspend operator fun invoke(name: String, songs: List<Song>, destination: String): Result = fileWriter.write(destination, m3uWriter.write(songs))
}
