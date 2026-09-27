package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_32_33 =
    object : Migration(32, 33) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE songs ADD COLUMN replayGainTrack REAL")
            connection.execSQL("ALTER TABLE songs ADD COLUMN replayGainAlbum REAL")
        }
    }
