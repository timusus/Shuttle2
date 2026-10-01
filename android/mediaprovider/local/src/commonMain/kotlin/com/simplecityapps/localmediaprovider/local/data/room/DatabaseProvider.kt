package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.RoomDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.localmediaprovider.local.data.room.migrations.ALL_MIGRATIONS

/** The database's file name, the same on every platform. */
const val DATABASE_NAME = "song.db"

/**
 * Opens [MediaDatabase] from a platform's [builder] (see `databaseBuilder`): every migration registered, and, outside
 * debug builds, a database too old or broken to migrate recreated empty rather than crashing.
 */
class DatabaseProvider(
    private val builder: RoomDatabase.Builder<MediaDatabase>,
    private val isDebug: Boolean
) {
    val database: MediaDatabase by lazy {
        builder
            .addMigrations(*ALL_MIGRATIONS)
            .trackingIdentityChanges()
            .apply {
                if (!isDebug) {
                    fallbackToDestructiveMigration(dropAllTables = false)
                }
            }
            .build()
    }
}
