package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds the album artist tag unsplit, since untagged classical credits read performers from a combined list. */
val MIGRATION_59_60 =
    object : Migration(59, 60) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `albumArtistDisplay` TEXT")
        }
    }
