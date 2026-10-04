package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Drops `pending_favourites.mediaProvider` and `externalId` (#497): the sender looks each song up by `songId`, so
 * nothing read them. SQLite before 3.35 (below API 34) can't drop a column, so the table is rebuilt, keeping its rows.
 *
 * Then backfills the outbox with every remote-provider favourite that has no pending row: hearts made before the outbox
 * existed were never sent, and the sync's favourite merge lets the server win for a song with no row, so they would be
 * cleared rather than pushed. Each is queued as a favourite at the time it was made.
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
            connection.execSQL(
                "INSERT INTO `pending_favourites` (`songId`, `favourite`, `changedAt`) " +
                    "SELECT `id`, 1, `favouritedAt` FROM `songs` " +
                    "WHERE `favouritedAt` IS NOT NULL AND `mediaProvider` IN ('Emby', 'Jellyfin', 'Plex') " +
                    "AND `id` NOT IN (SELECT `songId` FROM `pending_favourites`)"
            )
        }
    }
