package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Scopes the songs path index to (path, mediaProvider), so providers stop silently dropping each other's row for one file. */
val MIGRATION_43_44 =
    object : Migration(43, 44) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP INDEX IF EXISTS `index_songs_path`")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_songs_path_mediaProvider` ON `songs` (`path`, `mediaProvider`)")
        }
    }
