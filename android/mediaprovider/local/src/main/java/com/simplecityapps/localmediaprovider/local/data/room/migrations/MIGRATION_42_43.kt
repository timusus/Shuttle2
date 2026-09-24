package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_42_43 =
    object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pinned_collections` (`collectionType` TEXT NOT NULL, `collectionId` TEXT NOT NULL, `mediaProvider` TEXT NOT NULL, " +
                    "PRIMARY KEY(`collectionType`, `collectionId`, `mediaProvider`))"
            )
        }
    }
