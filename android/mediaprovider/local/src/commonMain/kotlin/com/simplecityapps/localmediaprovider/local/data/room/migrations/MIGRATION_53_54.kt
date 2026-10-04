package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** An index on a provider's songs by path, which the importer reads and diffs a provider's songs by. */
val MIGRATION_53_54 =
    object : Migration(53, 54) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_mediaProvider_path` ON `songs` (`mediaProvider`, `path`)")
        }
    }
