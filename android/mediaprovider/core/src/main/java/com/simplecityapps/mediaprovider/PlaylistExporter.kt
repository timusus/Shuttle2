package com.simplecityapps.mediaprovider

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * PlaylistExporter handles exporting playlists to m3u files.
 *
 * This class provides multiple export strategies to handle Android's content:// URI system
 * and the limitations around converting URIs to file paths.
 */
class PlaylistExporter(
    private val context: Context,
    private val m3uWriter: M3uWriter = M3uWriter()
) {

    /**
     * Export playlist to an m3u file at the specified URI.
     *
     * This uses Android's Storage Access Framework and writes the playlist content
     * to the provided document URI.
     *
     * @param playlistName Name of the playlist (used for logging)
     * @param songs List of songs to export
     * @param destinationUri URI where the m3u file should be written
     * @param pathResolver Optional function to resolve file paths for songs.
     *                     If null, uses the default resolver which attempts to extract
     *                     real paths from content URIs.
     * @return Result indicating success or failure with error message
     */
    suspend fun exportToUri(
        playlistName: String,
        songs: List<Song>,
        destinationUri: Uri,
        pathResolver: ((Song) -> String?)? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val resolver = pathResolver ?: ::resolvePathForSong
            val m3uContent = m3uWriter.write(songs, resolver)

            if (m3uContent == null) {
                return@withContext ExportResult.Failure("No valid songs found to export")
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                outputStream.write(m3uContent.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                ExportResult.Success(destinationUri)
            } ?: ExportResult.Failure("Could not open output stream for URI: $destinationUri")
        } catch (e: IOException) {
            Timber.e(e, "Failed to export playlist '$playlistName' to $destinationUri")
            ExportResult.Failure("IO error: ${e.message}")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to export playlist '$playlistName' to $destinationUri (permission denied)")
            ExportResult.Failure("Permission denied: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error exporting playlist '$playlistName'")
            ExportResult.Failure("Unexpected error: ${e.message}")
        }
    }

    /**
     * Export playlist to a directory, creating a new m3u file.
     *
     * @param playlistName Name of the playlist (used for filename)
     * @param songs List of songs to export
     * @param directoryUri URI of the directory where the file should be created
     * @param pathResolver Optional function to resolve file paths for songs
     * @return Result indicating success or failure with error message
     */
    suspend fun exportToDirectory(
        playlistName: String,
        songs: List<Song>,
        directoryUri: Uri,
        pathResolver: ((Song) -> String?)? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val directory = DocumentFile.fromTreeUri(context, directoryUri)
                ?: return@withContext ExportResult.Failure("Invalid directory URI")

            if (!directory.exists() || !directory.isDirectory) {
                return@withContext ExportResult.Failure("Directory does not exist or is not accessible")
            }

            // Sanitize playlist name for filename
            val fileName = sanitizeFileName(playlistName) + ".m3u"

            // Check if file already exists and delete it
            directory.findFile(fileName)?.delete()

            // Create new m3u file
            val newFile = directory.createFile("audio/x-mpegurl", fileName)
                ?: return@withContext ExportResult.Failure("Could not create file: $fileName")

            return@withContext exportToUri(playlistName, songs, newFile.uri, pathResolver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export playlist '$playlistName' to directory")
            ExportResult.Failure("Error creating file: ${e.message}")
        }
    }

    /**
     * Generate m3u content as a string without writing to a file.
     *
     * This can be used for sharing or when the caller wants to handle
     * the file writing themselves.
     *
     * @param songs List of songs to export
     * @param pathResolver Optional function to resolve file paths for songs
     * @return M3U file content as a string, or null if no valid songs
     */
    suspend fun generateM3uContent(
        songs: List<Song>,
        pathResolver: ((Song) -> String?)? = null
    ): String? = withContext(Dispatchers.IO) {
        val resolver = pathResolver ?: ::resolvePathForSong
        m3uWriter.write(songs, resolver)
    }

    /**
     * Attempts to resolve a real file path for a song.
     *
     * This is a best-effort approach:
     * 1. If the song path is already a file path (not content://), use it as-is
     * 2. If it's a content:// URI, try to extract the real path
     * 3. Fall back to using the content URI if no real path can be determined
     *
     * Note: Many m3u players may not be able to use content:// URIs,
     * so this is a known limitation when exporting playlists on Android.
     */
    private fun resolvePathForSong(song: Song): String? {
        return when {
            // If path doesn't start with content://, assume it's a real file path
            !song.path.startsWith("content://") -> song.path

            // Try to extract real path from content URI
            else -> {
                tryGetRealPath(song.path) ?: song.path
            }
        }
    }

    /**
     * Attempts to get a real file path from a content URI.
     *
     * This is a best-effort method and may not work for all URIs,
     * especially on Android 10+ with scoped storage.
     *
     * @param contentUri The content:// URI to resolve
     * @return Real file path if available, null otherwise
     */
    private fun tryGetRealPath(contentUri: String): String? {
        return try {
            val uri = Uri.parse(contentUri)

            // Try to get the document ID and extract path information
            // This is a limited approach and won't work for all cases
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            documentFile?.name?.let { fileName ->
                // If we have a filename but no full path, at least use the filename
                // This allows relative paths in m3u files which some players can handle
                fileName
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not resolve real path for: $contentUri")
            null
        }
    }

    /**
     * Sanitizes a filename by removing or replacing invalid characters.
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .trim()
            .take(255) // Max filename length on most systems
    }

    sealed class ExportResult {
        data class Success(val uri: Uri) : ExportResult()
        data class Failure(val error: String) : ExportResult()
    }
}
