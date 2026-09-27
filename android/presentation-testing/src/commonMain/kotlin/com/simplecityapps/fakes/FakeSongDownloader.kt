package com.simplecityapps.fakes

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records the downloads started and removed; a song whose path is in [unavailable] has nothing to download. */
class FakeSongDownloader : SongDownloader {
    val unavailable = mutableSetOf<String>()
    val downloaded = mutableListOf<Song>()
    val removed = mutableListOf<Song>()
    val heldPaths = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun download(song: Song): Boolean {
        if (song.path in unavailable) return false
        downloaded += song
        return true
    }

    override fun remove(song: Song) {
        removed += song
    }

    override fun observeHeldPaths(): Flow<Set<String>> = heldPaths
}
