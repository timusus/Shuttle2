package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_37_38 =
    object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN bitRate INTEGER")
            db.execSQL("ALTER TABLE songs ADD COLUMN bitDepth INTEGER")
            db.execSQL("ALTER TABLE songs ADD COLUMN sampleRate INTEGER")
            db.execSQL("ALTER TABLE songs ADD COLUMN channelCount INTEGER")
        }
    }
