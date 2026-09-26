package com.simplecityapps.shuttle.ui.actions

import android.net.Uri
import com.simplecityapps.mediaprovider.PlaylistExporter
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** Writes [songs] as an m3u under [name] to [destination], a content Uri string from the document picker. */
class ExportPlaylist @Inject constructor(
    private val playlistExporter: PlaylistExporter,
) {
    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    suspend operator fun invoke(name: String, songs: List<Song>, destination: String): Result = when (val result = playlistExporter.exportToUri(name, songs, Uri.parse(destination))) {
        is PlaylistExporter.ExportResult.Success -> Result.Success
        is PlaylistExporter.ExportResult.Failure -> Result.Failure(result.error)
    }
}
