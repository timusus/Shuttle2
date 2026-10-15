package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The classical metadata of #1065 as nullable columns on `songs`: composer, conductor, arranger, work, movement and the
 * performers (';'-joined like `artists`). Additive only, so every row keeps its id and everything keyed by it; the columns
 * stay null until the re-import that `MediaImporter.SONG_TAGS_VERSION` 6 triggers reads each song again.
 */
val MIGRATION_58_59 =
    object : Migration(58, 59) {
        override fun migrate(connection: SQLiteConnection) {
            listOf(
                "composer" to "TEXT",
                "composerSort" to "TEXT",
                "conductor" to "TEXT",
                "arranger" to "TEXT",
                "work" to "TEXT",
                "movementName" to "TEXT",
                "movementNumber" to "INTEGER",
                "movementTotal" to "INTEGER",
                "showMovement" to "INTEGER",
                "performers" to "TEXT"
            ).forEach { (column, type) ->
                connection.execSQL("ALTER TABLE `songs` ADD COLUMN `$column` $type")
            }
        }
    }
