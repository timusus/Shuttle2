package com.simplecityapps.localmediaprovider.local.data.room

import androidx.room.RoomDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.localmediaprovider.local.data.room.migrations.ALL_MIGRATIONS

/** The database's file name, the same on every platform. */
const val DATABASE_NAME = "song.db"

/** The oldest version [ALL_MIGRATIONS] can migrate from; earlier databases predate the migration chain. */
private const val OLDEST_MIGRATABLE_VERSION = 23

/**
 * Opens [MediaDatabase] from a platform's [builder] (see `databaseBuilder`): every migration registered. A missing
 * migration fails loudly rather than wiping play history, favourites and playlists; only databases older than
 * [OLDEST_MIGRATABLE_VERSION], which have no migration path, are recreated empty.
 */
class DatabaseProvider(
    private val builder: RoomDatabase.Builder<MediaDatabase>
) {
    val database: MediaDatabase by lazy {
        builder
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(false, *(1 until OLDEST_MIGRATABLE_VERSION).toList().toIntArray())
            .trackingIdentityChanges()
            .build()
    }
}
