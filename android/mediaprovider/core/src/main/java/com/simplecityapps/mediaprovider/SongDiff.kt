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
        lastModified = newData.lastModified ?: oldData.lastModified
    )
}
