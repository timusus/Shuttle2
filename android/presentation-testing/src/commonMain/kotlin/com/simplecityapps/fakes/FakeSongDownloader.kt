package com.simplecityapps.fakes

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.SongDownloader

/** Records the downloads started and removed; a song whose path is in [unavailable] has nothing to download. */
class FakeSongDownloader : SongDownloader {
    val unavailable = mutableSetOf<String>()
    val downloaded = mutableListOf<Song>()
    val removed = mutableListOf<Song>()

    override suspend fun download(song: Song): Boolean {
        if (song.path in unavailable) return false
        downloaded += song
        return true
    }

    override fun remove(song: Song) {
        removed += song
    }
}
