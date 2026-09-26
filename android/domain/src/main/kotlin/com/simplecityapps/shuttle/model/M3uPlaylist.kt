package com.simplecityapps.shuttle.model

class Entry(
    val location: String,
    val duration: Int?,
    val artist: String?,
    val track: String?,
    /** The entry's original lines (any `#EXTINF` line followed by the location line) verbatim, for re-inserting unresolved entries on sync without reformatting them. */
    val rawLines: List<String> = emptyList()
)

data class M3uPlaylist(
    val path: String,
    val name: String,
    val entries: List<Entry>
)
