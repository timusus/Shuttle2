package com.simplecityapps.shuttle.common.database

import com.simplecityapps.shuttle.model.SongData
import comsimplecityappsshuttle.common.database.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

interface SongSharedDatabase {

    fun getSongs(): Flow<List<SongEntity>>

    suspend fun insertOrUpdate(songs: List<SongData>, insertDate: Instant = Clock.System.now())
}