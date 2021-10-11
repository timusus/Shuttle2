package com.simplecityapps.shuttle.repository

import com.simplecityapps.shuttle.common.database.SongSharedDatabase
import com.simplecityapps.shuttle.mapper.toSong
import com.simplecityapps.shuttle.model.SongData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SongRepository(
    private val database: SongSharedDatabase,
    scope: CoroutineScope
) {

    val songs = database
        .getSongs()
        .map { entities -> entities.map { songEntity -> songEntity.toSong() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    suspend fun insertSongs(songs: List<SongData>) {
        database.insertOrUpdate(songs)
    }
}