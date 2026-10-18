package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** The server item whose image stands in for a song's own; null until the next Emby or Jellyfin sync fills it in. */
val MIGRATION_57_58 =
    object : Migration(57, 58) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `artworkItemId` TEXT")
        }
    }
