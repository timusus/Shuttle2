package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.M3uPlaylist

class M3uParser {
    /** Parses [text], a whole m3u file read as UTF-8. */
    fun parse(
        path: String,
        fileName: String,
        text: String
    ): M3uPlaylist {
        val entries = mutableListOf<Entry>()
        var duration: Int? = null
        var artist: String? = null
        var track: String? = null
        var extinfLine: String? = null
        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim().let { if (index == 0) it.replace("\ufeff", "") else it }
            when {
                line.isBlank() -> {
                }

                line.startsWith("#") -> {
                    if (line.startsWith("#EXTINF:")) {
                        extinfLine = line
                        duration = line.substringAfter("#EXTINF:").substringBefore(',').toIntOrNull()
                        val remainder = line.substringAfter(',')
                        artist = remainder.substringBefore('-').trim()
                        track = remainder.substringAfter('-').trim()
                    }
                }

                else -> {
                    entries.add(Entry(line.sanitise(), duration, artist, track, rawLines = listOfNotNull(extinfLine, line)))
                    duration = null
                    artist = null
                    track = null
                    extinfLine = null
                }
            }
        }

        return M3uPlaylist(path, fileName.substringBeforeLast("."), entries)
    }

    /**
     * Sanitises paths, converting windows file separators to unix, and removing any leading windows directory qualifiers (e.g. c:\)
     */
    fun String.sanitise(): String = substringAfter(":\\").replace('\\', '/')
}
