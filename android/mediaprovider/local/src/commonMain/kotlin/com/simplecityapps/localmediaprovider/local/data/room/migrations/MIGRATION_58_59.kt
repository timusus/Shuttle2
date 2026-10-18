package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds nullable classical metadata columns; additive, filled in by the re-import that SONG_TAGS_VERSION 6 triggers. */
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
