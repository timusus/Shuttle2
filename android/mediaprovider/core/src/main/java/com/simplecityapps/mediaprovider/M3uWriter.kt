package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

/**
 * M3uWriter generates m3u playlist file content from a list of songs.
 *
 * The generated m3u file follows the extended m3u format:
 * - Starts with #EXTM3U header
 * - Each song entry contains:
 *   - #EXTINF:<duration in seconds>, <artist> - <title>
 *   - <file path>
 */
class M3uWriter {

    /**
     * Generates m3u file content from a list of songs and their file paths.
     *
     * @param songs List of songs to include in the playlist
     * @param pathResolver Function to resolve the file path for each song.
     *                     Should return null if the path cannot be resolved.
     * @return M3U file content as a string, or null if no valid songs were found
     */
    fun write(
        songs: List<Song>,
        pathResolver: (Song) -> String?
    ): String? {
        val builder = StringBuilder()
        builder.appendLine("#EXTM3U")
        builder.appendLine()

        var validSongCount = 0

        songs.forEach { song ->
            val path = pathResolver(song)
            if (path != null) {
                // Convert duration from milliseconds to seconds
                val durationSeconds = song.duration / 1000

                // Format: artist - title
                val artistName = song.friendlyArtistName ?: "Unknown Artist"
                val trackName = song.name ?: "Unknown Track"
                val info = "$artistName - $trackName"

                builder.appendLine("#EXTINF:$durationSeconds, $info")
                builder.appendLine(path)
                builder.appendLine()

                validSongCount++
            }
        }

        // Return null if no valid songs were found
        return if (validSongCount > 0) builder.toString() else null
    }

    /**
     * Simplified version that writes m3u content using song paths as-is.
     * Useful when all songs already have valid file paths.
     *
     * @param songs List of songs to include in the playlist
     * @return M3U file content as a string
     */
    fun write(songs: List<Song>): String = write(songs) { it.path } ?: ""
}
