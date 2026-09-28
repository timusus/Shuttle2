package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

class SongDiff(existingData: List<Song>, newData: List<Song>) : Diff<Song>(existingData, newData) {
    override fun isEqual(
        a: Song,
        b: Song
    ): Boolean = a.path == b.path

    override fun update(
        oldData: Song,
        newData: Song
    ): Song = newData.copy(
        id = oldData.id,
        // A provider with no date for the song keeps the one from its first import, rather than looking newly added
        lastModified = newData.lastModified ?: oldData.lastModified,
        // The server's date for a remote song, which replaces an older import stamp; otherwise (local songs) the one
        // stamped when the song first reached the library, which a tag edit or rescan doesn't move
        dateAdded = newData.dateAdded ?: oldData.dateAdded
    )
}
