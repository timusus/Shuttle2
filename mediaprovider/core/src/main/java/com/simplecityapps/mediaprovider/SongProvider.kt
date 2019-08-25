package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Observable

interface SongProvider {
    fun findSongs(): Observable<Pair<Song, Float>>
}