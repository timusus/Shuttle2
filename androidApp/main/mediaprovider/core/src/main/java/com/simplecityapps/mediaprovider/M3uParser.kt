package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.M3uPlaylist
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class M3uParser {

    fun parse(path: String, fileName: String, inputStream: InputStream): M3uPlaylist {
        val entries = mutableListOf<Entry>()
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String? = reader.readLine()?.trim()?.replace("\ufeff", "")
            var duration: Int? = null
            var artist: String? = null
            var track: String? = null
            while (line != null) {
                when {
                    line.isBlank() -> {
                    }
                    line.startsWith("#") -> {
                        if (line.startsWith("#EXTINF:")) {
                            duration = line.substringAfter("#EXTINF:").substringBefore(',').toIntOrNull()
                            val remainder = line.substringAfter(',')
                            artist = remainder.substringBefore('-').trim()
                            track = remainder.substringAfter('-').trim()
                        }
                    }
                    else -> {
                        entries.add(Entry(line.sanitise(), duration, artist, track))
                        duration = null
                        artist = null
                        track = null
                    }
                }
                line = reader.readLine()?.trim()
            }
        }

        return M3uPlaylist(path, fileName.substringBeforeLast("."), entries)
    }

    /**
     * Sanitises paths, converting windows file separators to unix, and removing any leading windows directory qualifiers (e.g. c:\)
     */
    fun String.sanitise(): String {
        return substringAfter(":\\").replace('\\', '/')
    }
}
