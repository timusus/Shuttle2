package com.simplecityapps.shuttle.repository

import com.simplecityapps.shuttle.common.database.SongSharedDatabase
import com.simplecityapps.shuttle.mapper.toSong
import com.simplecityapps.shuttle.mapper.toSongEntity
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SongRepository(
    private val database: SongSharedDatabase,
    private val scope: CoroutineScope
) {

    val songs = database
        .observeAll()
        .map { entities -> entities.map { songEntity -> songEntity.toSong() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    suspend fun insertSongs(songs: List<Song>) {
        database.insertOrUpdate(songs.map { song -> song.toSongEntity() })
    }
}