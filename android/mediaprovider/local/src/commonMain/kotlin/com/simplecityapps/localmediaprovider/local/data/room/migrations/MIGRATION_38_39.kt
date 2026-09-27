package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_38_39 =
    object : Migration(38, 39) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE IF NOT EXISTS playlists2 (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, sortOrder TEXT NOT NULL DEFAULT 'Position', `mediaProvider` TEXT NOT NULL DEFAULT 'Shuttle',`externalId` TEXT)")
            connection.execSQL(
                "INSERT INTO playlists2 (id, name, sortOrder) " +
                    "SELECT id, name, sortOrder " +
                    "FROM playlists"
            )
            connection.execSQL("DROP TABLE playlists")
            connection.execSQL("ALTER TABLE playlists2 RENAME TO playlists")
        }
    }
