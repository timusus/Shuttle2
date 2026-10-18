package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Creates identity_generation so a play no longer rebuilds the album index. */
val MIGRATION_51_52 =
    object : Migration(51, 52) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `identity_generation` (`id` INTEGER NOT NULL, `generation` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
        }
    }
