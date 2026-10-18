package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Creates the pending_favourites outbox; mediaProvider and externalId are copied in at write time so the writer needn't look the song up. */
val MIGRATION_47_48 =
    object : Migration(47, 48) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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
