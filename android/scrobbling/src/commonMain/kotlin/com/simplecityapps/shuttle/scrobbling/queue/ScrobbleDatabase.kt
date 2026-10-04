package com.simplecityapps.shuttle.scrobbling.queue

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * The scrobbling module's own database (`scrobbles.db`), separate from the library's MediaDatabase so scrobbling never
 * needs a migration of the main library database. Starts at version 1: there's nothing to migrate from, since this
 * table didn't exist before slice 2. Android builds it with a Context (AndroidScrobblingModule), iOS in Application
 * Support (`:shared`'s IosScrobblingModule).
 */
@Database(entities = [QueuedScrobbleEntity::class], version = 1, exportSchema = true)
@ConstructedBy(ScrobbleDatabaseConstructor::class)
abstract class ScrobbleDatabase : RoomDatabase() {
    abstract fun scrobbleDao(): ScrobbleDao

    companion object {
        const val DATABASE_NAME = "scrobbles.db"
    }
}

// Room's compiler generates the actual for each target.
@Suppress("KotlinNoActualForExpect")
expect object ScrobbleDatabaseConstructor : RoomDatabaseConstructor<ScrobbleDatabase> {
    override fun initialize(): ScrobbleDatabase
}
