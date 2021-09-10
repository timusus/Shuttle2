package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

class SongDiff(existingData: List<Song>, newData: List<Song>) : Diff<Song>(existingData, newData) {
    override fun isEqual(a: Song, b: Song): Boolean {
        return a.path == b.path
    }

    override fun update(oldData: Song, newData: Song): Song {
        return newData.copy(id = oldData.id)
    }
}