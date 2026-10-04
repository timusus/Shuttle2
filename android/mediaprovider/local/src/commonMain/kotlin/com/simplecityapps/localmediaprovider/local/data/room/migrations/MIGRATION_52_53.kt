package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Drops `pending_favourites.mediaProvider` and `externalId` (#497): the sender looks each song up by `songId`, so
 * nothing read them. SQLite before 3.35 (below API 34) can't drop a column, so the table is rebuilt, keeping its rows.
 */
val MIGRATION_52_53 =
    object : Migration(52, 53) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_favourites_new` (" +
                    "`songId` INTEGER NOT NULL, " +
                    "`favourite` INTEGER NOT NULL, " +
                    "`changedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`songId`), " +
                    "FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            connection.execSQL("INSERT INTO `pending_favourites_new` (`songId`, `favourite`, `changedAt`) SELECT `songId`, `favourite`, `changedAt` FROM `pending_favourites`")
            connection.execSQL("DROP TABLE `pending_favourites`")
            connection.execSQL("ALTER TABLE `pending_favourites_new` RENAME TO `pending_favourites`")
        }
    }
