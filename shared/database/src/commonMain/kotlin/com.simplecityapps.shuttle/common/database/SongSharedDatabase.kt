package com.simplecityapps.shuttle.common.database

import comsimplecityappsshuttle.common.database.SongEntity
import kotlinx.coroutines.flow.Flow

interface SongSharedDatabase {

    fun observeAll(): Flow<List<SongEntity>>

    suspend fun insertOrUpdate(songs: List<SongEntity>)

}