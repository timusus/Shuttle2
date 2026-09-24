package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

/**
 * Generates extended m3u playlist content from a list of songs, using [Song.path] as-is.
 * The importer ([M3uParser] via `TaglibMediaProvider.findPlaylists`) matches entries back to
 * songs by filename, not full path equality, so a raw device path round-trips safely.
 */
class M3uWriter {
    fun write(songs: List<Song>): String {
        val builder = StringBuilder()
        builder.appendLine("#EXTM3U")
        builder.appendLine()

        songs.forEach { song ->
            val durationSeconds = song.duration / 1000
            val artistName = song.friendlyArtistName ?: "Unknown Artist"
            val trackName = song.name ?: "Unknown Track"

            builder.appendLine("#EXTINF:$durationSeconds, $artistName - $trackName")
            builder.appendLine(song.path)
            builder.appendLine()
        }

        return builder.toString()
    }
}
