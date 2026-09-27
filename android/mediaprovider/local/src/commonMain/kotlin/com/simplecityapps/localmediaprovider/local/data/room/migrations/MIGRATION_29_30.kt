package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_29_30 =
    object : Migration(29, 30) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE songs ADD COLUMN artist TEXT NOT NULL DEFAULT ''")
            connection.execSQL("UPDATE songs SET artist = albumArtist")
        }
    }
