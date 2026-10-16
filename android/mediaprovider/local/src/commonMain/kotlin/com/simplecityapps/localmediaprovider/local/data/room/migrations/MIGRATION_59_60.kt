package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The album artist tag as written, unsplit, on `songs` (#1065): untagged classical credits read the performers a combined
 * "Mahler; Janet Baker, Simon Rattle" lists after the composer. Additive and null until the re-import that
 * `MediaImporter.SONG_TAGS_VERSION` 7 triggers reads each song again.
 */
val MIGRATION_59_60 =
    object : Migration(59, 60) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `albumArtistDisplay` TEXT")
        }
    }
