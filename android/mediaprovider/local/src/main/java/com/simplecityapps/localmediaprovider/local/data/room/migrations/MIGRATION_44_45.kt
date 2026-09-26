package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * User smart playlists (#506): `smart_playlists` holds each one's name and its rules as JSON, and `songs.dateAdded`
 * records when a song reached the library, for a "date added" rule. Existing songs take their `lastModified` as the best
 * guess there is; "Recently added" has used `lastModified` until now.
 */
val MIGRATION_44_45 =
    object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `dateAdded` INTEGER")
            db.execSQL("UPDATE `songs` SET `dateAdded` = `lastModified`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `smart_playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`rulesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
        }
    }
