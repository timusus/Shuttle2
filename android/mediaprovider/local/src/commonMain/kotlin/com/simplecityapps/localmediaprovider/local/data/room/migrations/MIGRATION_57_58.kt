package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** The server item whose image stands in for a song's own (#1048). Null until the next sync of an Emby or Jellyfin library fills it in: an incremental sync resolves the stored songs too. */
val MIGRATION_57_58 =
    object : Migration(57, 58) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `artworkItemId` TEXT")
        }
    }
