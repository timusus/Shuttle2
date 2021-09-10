package com.simplecityapps.shuttle.common.database

import com.simplecityapps.shuttle.database.SongDatabase
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import comsimplecityappsshuttle.common.database.SongEntity
import kotlinx.coroutines.flow.Flow

class DefaultSongSharedDatabase(private val driver: SqlDriver) : SongSharedDatabase {

    override fun observeAll(): Flow<List<SongEntity>> {
        return SongDatabase(driver).songDatabaseQueries.selectAll().asFlow().mapToList()
    }
}