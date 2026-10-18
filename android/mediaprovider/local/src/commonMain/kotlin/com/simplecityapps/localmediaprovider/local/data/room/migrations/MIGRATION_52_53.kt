package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Rebuilds pending_favourites without its unused columns, and queues hearts made before the outbox existed so a sync doesn't clear them. */
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
            connection.execSQL(
                "INSERT INTO `pending_favourites` (`songId`, `favourite`, `changedAt`) " +
                    "SELECT `id`, 1, `favouritedAt` FROM `songs` " +
                    "WHERE `favouritedAt` IS NOT NULL AND `mediaProvider` IN ('Emby', 'Jellyfin', 'Plex') " +
                    "AND `id` NOT IN (SELECT `songId` FROM `pending_favourites`)"
            )
        }
    }
