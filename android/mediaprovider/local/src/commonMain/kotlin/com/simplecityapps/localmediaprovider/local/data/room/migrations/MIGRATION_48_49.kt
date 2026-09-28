package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * `play_events` is the listening history (#633): one row per song played through, or listened to for 30s before playback
 * moved off it, with the context its queue was started from. Backfilled with one play through per song that has a
 * `lastCompleted`, at that time, with its local hour and ISO weekday as SQLite's `localtime` has them, from no context.
 */
val MIGRATION_48_49 =
    object : Migration(48, 49) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `play_events` (" +
                    "`mediaProvider` TEXT NOT NULL, " +
                    "`songPath` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, " +
                    "`listenedMs` INTEGER NOT NULL, " +
                    "`completed` INTEGER NOT NULL, " +
                    "`localHour` INTEGER NOT NULL, " +
                    "`weekday` INTEGER NOT NULL, " +
                    "`contextType` TEXT NOT NULL, " +
                    "`contextId` TEXT, " +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_startedAt` ON `play_events` (`startedAt`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_play_events_contextType_contextId` ON `play_events` (`contextType`, `contextId`)")
            // strftime('%w') is 0 for Sunday; ISO weekdays run 1 (Monday) to 7 (Sunday).
            connection.execSQL(
                "INSERT INTO `play_events` (mediaProvider, songPath, startedAt, listenedMs, completed, localHour, weekday, contextType, contextId) " +
                    "SELECT mediaProvider, path, lastCompleted, duration, 1, " +
                    "CAST(strftime('%H', lastCompleted / 1000, 'unixepoch', 'localtime') AS INTEGER), " +
                    "(CAST(strftime('%w', lastCompleted / 1000, 'unixepoch', 'localtime') AS INTEGER) + 6) % 7 + 1, " +
                    "'none', NULL " +
                    "FROM songs WHERE lastCompleted IS NOT NULL"
            )
        }
    }
