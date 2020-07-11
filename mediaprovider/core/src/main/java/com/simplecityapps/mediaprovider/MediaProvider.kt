package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow

interface MediaProvider {
    fun findSongs(): Flow<Pair<Song, Float>>
}