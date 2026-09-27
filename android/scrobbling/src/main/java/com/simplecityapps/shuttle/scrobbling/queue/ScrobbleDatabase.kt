package com.simplecityapps.shuttle.scrobbling.queue

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The scrobbling module's own database (`scrobbles.db`), separate from [com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase]
 * so scrobbling never needs a migration of the main library database. Starts at version 1: there's nothing to
 * migrate from, since this table didn't exist before slice 2.
 */
@Database(entities = [QueuedScrobbleEntity::class], version = 1, exportSchema = true)
abstract class ScrobbleDatabase : RoomDatabase() {
    abstract fun scrobbleDao(): ScrobbleDao

    companion object {
        const val DATABASE_NAME = "scrobbles.db"
    }
}
