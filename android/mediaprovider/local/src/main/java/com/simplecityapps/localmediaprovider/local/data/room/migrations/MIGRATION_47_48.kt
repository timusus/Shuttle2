package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `pending_favourites` is the local outbox for a favourite/unfavourite made on a remote-provider song (#497): one row
 * per song, holding the desired state until a later slice pushes it to the server. `mediaProvider` and `externalId`
 * are copied in at write time so the writer doesn't need to look the song back up. No existing data needs migrating;
 * the table starts empty.
 */
val MIGRATION_47_48 =
    object : Migration(47, 48) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_favourites` (" +
                    "`songId` INTEGER NOT NULL, " +
                    "`mediaProvider` TEXT NOT NULL, " +
                    "`externalId` TEXT, " +
                    "`favourite` INTEGER NOT NULL, " +
                    "`changedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`songId`), " +
                    "FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
    }
