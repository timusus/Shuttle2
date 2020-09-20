package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.model.Song

interface MediaProvider {
    suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> (Unit))? = null): List<Song>?
}