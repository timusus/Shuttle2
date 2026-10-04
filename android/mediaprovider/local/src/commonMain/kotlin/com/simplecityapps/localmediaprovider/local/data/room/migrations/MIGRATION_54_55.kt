package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Indexes on when a song was last completed and when it was added, which Home pages its recently-played and recently-added songs by. */
val MIGRATION_54_55 =
    object : Migration(54, 55) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_lastCompleted` ON `songs` (`lastCompleted`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_dateAdded` ON `songs` (`dateAdded`)")
        }
    }
