package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds songs.dateAdded for the "date added" rule; existing songs take lastModified as the best guess. */
val MIGRATION_44_45 =
    object : Migration(44, 45) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `dateAdded` INTEGER")
            connection.execSQL("UPDATE `songs` SET `dateAdded` = `lastModified`")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `smart_playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`rulesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
        }
    }
