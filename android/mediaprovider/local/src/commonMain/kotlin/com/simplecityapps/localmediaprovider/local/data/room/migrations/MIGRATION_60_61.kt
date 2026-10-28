package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds the library generation, so a play-stat or favourite write no longer re-reads the whole library. Its row and triggers are installed on open. */
val MIGRATION_60_61 =
    object : Migration(60, 61) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `library_generation` (`id` INTEGER NOT NULL, `generation` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        }
    }
