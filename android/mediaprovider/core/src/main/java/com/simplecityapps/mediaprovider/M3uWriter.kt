package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.Song

/**
 * Generates extended m3u playlist content from a list of songs, using [Song.path] as-is.
 * The importer ([M3uParser] via `TaglibMediaProvider.findPlaylists`) matches entries back to
 * songs by filename, not full path equality, so a raw device path round-trips safely.
 */
class M3uWriter {
    fun write(songs: List<Song>): String = write(songs, preservedEntries = emptyMap())

    /**
     * [preservedEntries] are entries from the previous file that don't resolve to any library
     * song (moved, unscanned, or a remote URL) and so aren't tracked as playlist songs - they're
     * reinserted verbatim, keyed by the id of the song they should follow (`null` = before the
     * first song), so `LocalPlaylistRepository.syncM3uFile` doesn't silently drop them on rewrite.
     */
    fun write(
        songs: List<Song>,
        preservedEntries: Map<Long?, List<Entry>>
    ): String {
        val builder = StringBuilder()
        builder.appendLine("#EXTM3U")
        builder.appendLine()

        fun appendPreserved(anchor: Long?) {
            preservedEntries[anchor]?.forEach { entry ->
                entry.rawLines.forEach { builder.appendLine(it) }
                builder.appendLine()
            }
        }

        appendPreserved(anchor = null)
        songs.forEach { song ->
            val durationSeconds = song.duration / 1000
            val artistName = song.friendlyArtistName ?: "Unknown Artist"
            val trackName = song.name ?: "Unknown Track"

            builder.appendLine("#EXTINF:$durationSeconds, $artistName - $trackName")
            builder.appendLine(song.path)
            builder.appendLine()

            appendPreserved(anchor = song.id)
        }

        return builder.toString()
    }
}
