package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_30_31 =
    object : Migration(30, 31) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE songs ADD COLUMN genres TEXT NOT NULL DEFAULT ''")
        }
    }
