package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add rating column to songs table (0-5 star rating, 0 = unrated)
            db.execSQL("ALTER TABLE songs ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
        }
    }
