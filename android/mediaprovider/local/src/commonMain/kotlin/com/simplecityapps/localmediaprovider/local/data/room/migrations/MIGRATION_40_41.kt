package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `playlists` ADD COLUMN `sortDescending` INTEGER NOT NULL DEFAULT 0")
        }
    }
