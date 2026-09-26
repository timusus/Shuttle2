package com.simplecityapps.mediaprovider

import android.content.Context
import android.net.Uri
import com.simplecityapps.shuttle.model.Song
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Writes a playlist's songs to an m3u file at a caller-chosen [Uri] (a SAF document, typically
 * from `ActivityResultContracts.CreateDocument`).
 */
class PlaylistExporter(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val m3uWriter: M3uWriter = M3uWriter()
) {
    suspend fun exportToUri(
        playlistName: String,
        songs: List<Song>,
        destinationUri: Uri
    ): ExportResult = withContext(ioDispatcher) {
        try {
            val content = m3uWriter.write(songs)
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                ExportResult.Success(destinationUri)
            } ?: ExportResult.Failure("Could not open output stream for URI: $destinationUri")
        } catch (e: IOException) {
            Timber.e(e, "Failed to export playlist '$playlistName' to $destinationUri")
            ExportResult.Failure("IO error: ${e.message}")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to export playlist '$playlistName' to $destinationUri (permission denied)")
            ExportResult.Failure("Permission denied: ${e.message}")
        }
    }

    sealed class ExportResult {
        data class Success(val uri: Uri) : ExportResult()
        data class Failure(val error: String) : ExportResult()
    }
}
