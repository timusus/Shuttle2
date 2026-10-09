package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Keys the stored MediaStore listing by local provider, so each one's diff and moved-file matching compares against the
 * listing its own last import read. The listing so far is copied to each local provider with songs: the closest either
 * has to its own, and a provider without one would read MediaStore whole and miss the files moved since.
 */
val MIGRATION_56_57 =
    object : Migration(56, 57) {
        override fun migrate(connection: SQLiteConnection) {
            val localProviders = "(SELECT DISTINCT mediaProvider FROM songs WHERE mediaProvider IN ('Shuttle', 'MediaStore'))"
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `media_store_files_new` (`provider` TEXT NOT NULL, `id` INTEGER NOT NULL, `generation` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                    "`displayName` TEXT NOT NULL, `size` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, `mimeType` TEXT, `duration` INTEGER, PRIMARY KEY(`provider`, `id`))"
            )
            connection.execSQL(
                "INSERT INTO media_store_files_new (provider, id, generation, path, displayName, size, lastModified, mimeType, duration) " +
                    "SELECT p.mediaProvider, f.id, f.generation, f.path, f.displayName, f.size, f.lastModified, f.mimeType, f.duration FROM media_store_files f, $localProviders p"
            )
            connection.execSQL("DROP TABLE media_store_files")
            connection.execSQL("ALTER TABLE media_store_files_new RENAME TO media_store_files")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `media_store_scan_state_new` (`provider` TEXT NOT NULL, `version` TEXT NOT NULL, PRIMARY KEY(`provider`))")
            connection.execSQL("INSERT INTO media_store_scan_state_new (provider, version) SELECT p.mediaProvider, s.version FROM media_store_scan_state s, $localProviders p")
            connection.execSQL("DROP TABLE media_store_scan_state")
            connection.execSQL("ALTER TABLE media_store_scan_state_new RENAME TO media_store_scan_state")
        }
    }
