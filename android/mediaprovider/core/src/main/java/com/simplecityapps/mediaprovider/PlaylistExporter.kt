package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException

/**
 * Utility class for exporting playlists to various formats
 */
class PlaylistExporter(
    private val playlistRepository: PlaylistRepository
) {
    /**
     * Export a playlist to M3U format (Extended M3U with metadata)
     *
     * @param playlist The playlist to export
     * @param outputFile The file to write the M3U content to
     * @param useRelativePaths If true, use relative paths instead of absolute paths
     * @throws IOException if writing to file fails
     */
    suspend fun exportToM3U(
        playlist: Playlist,
        outputFile: File,
        useRelativePaths: Boolean = false
    ) {
        val songs = playlistRepository.getSongsForPlaylist(playlist).first()

        outputFile.bufferedWriter().use { writer ->
            // Write M3U header
            writer.write("#EXTM3U\n")
            writer.write("#PLAYLIST:${playlist.name}\n")
            writer.write("\n")

            // Write each song
            songs.forEach { playlistSong ->
                val song = playlistSong.song

                // Write extended info: #EXTINF:duration,artist - title
                val durationInSeconds = song.duration / 1000
                val artist = song.friendlyArtistName ?: "Unknown Artist"
                val title = song.name ?: "Unknown Title"
                writer.write("#EXTINF:$durationInSeconds,$artist - $title\n")

                // Write file path
                val path = if (useRelativePaths) {
                    File(song.path).name
                } else {
                    song.path
                }
                writer.write("$path\n")
            }
        }
    }

    /**
     * Export a playlist to M3U8 format (UTF-8 encoded M3U)
     *
     * @param playlist The playlist to export
     * @param outputFile The file to write the M3U8 content to
     * @param useRelativePaths If true, use relative paths instead of absolute paths
     * @throws IOException if writing to file fails
     */
    suspend fun exportToM3U8(
        playlist: Playlist,
        outputFile: File,
        useRelativePaths: Boolean = false
    ) {
        // M3U8 is just UTF-8 encoded M3U, so we can reuse the same logic
        exportToM3U(playlist, outputFile, useRelativePaths)
    }

    /**
     * Generate M3U content as a string
     *
     * @param playlist The playlist to export
     * @param useRelativePaths If true, use relative paths instead of absolute paths
     * @return The M3U content as a string
     */
    suspend fun generateM3UContent(
        playlist: Playlist,
        useRelativePaths: Boolean = false
    ): String {
        val songs = playlistRepository.getSongsForPlaylist(playlist).first()

        return buildString {
            // Write M3U header
            append("#EXTM3U\n")
            append("#PLAYLIST:${playlist.name}\n")
            append("\n")

            // Write each song
            songs.forEach { playlistSong ->
                val song = playlistSong.song

                // Write extended info: #EXTINF:duration,artist - title
                val durationInSeconds = song.duration / 1000
                val artist = song.friendlyArtistName ?: "Unknown Artist"
                val title = song.name ?: "Unknown Title"
                append("#EXTINF:$durationInSeconds,$artist - $title\n")

                // Write file path
                val path = if (useRelativePaths) {
                    File(song.path).name
                } else {
                    song.path
                }
                append("$path\n")
            }
        }
    }
}
