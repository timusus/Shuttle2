package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_37_38 =
    object : Migration(37, 38) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE songs ADD COLUMN bitRate INTEGER")
            connection.execSQL("ALTER TABLE songs ADD COLUMN bitDepth INTEGER")
            connection.execSQL("ALTER TABLE songs ADD COLUMN sampleRate INTEGER")
            connection.execSQL("ALTER TABLE songs ADD COLUMN channelCount INTEGER")
        }
    }
