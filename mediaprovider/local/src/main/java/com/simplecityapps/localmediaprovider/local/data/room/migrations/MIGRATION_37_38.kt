package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE songs ADD COLUMN bitRate INTEGER")
        database.execSQL("ALTER TABLE songs ADD COLUMN bitDepth INTEGER")
        database.execSQL("ALTER TABLE songs ADD COLUMN sampleRate INTEGER")
        database.execSQL("ALTER TABLE songs ADD COLUMN channelCount INTEGER")
    }
}