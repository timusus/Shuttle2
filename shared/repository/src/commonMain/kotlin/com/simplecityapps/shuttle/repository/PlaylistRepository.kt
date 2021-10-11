package com.simplecityapps.shuttle.repository

import com.simplecityapps.shuttle.common.database.SongSharedDatabase
import com.simplecityapps.shuttle.mapper.toSong
import com.simplecityapps.shuttle.model.PlaylistData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlaylistRepository(
    private val database: SongSharedDatabase,
    private val scope: CoroutineScope
) {

    val playlists = database
        .getSongs()
        .map { entities -> entities.map { songEntity -> songEntity.toSong() } }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    suspend fun insertPlaylists(playlists: List<PlaylistData>) {

    }
}