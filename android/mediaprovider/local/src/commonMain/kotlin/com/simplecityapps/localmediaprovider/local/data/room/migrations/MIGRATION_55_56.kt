package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The MediaStore listing the last local import read, by MediaStore id with each row's generation, and the MediaStore
 * version it came from, so a rescan reads only the rows that changed (#875). Empty at first: the next import reads
 * MediaStore whole and fills them.
 */
val MIGRATION_55_56 =
    object : Migration(55, 56) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `media_store_files` (`id` INTEGER NOT NULL, `generation` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                    "`displayName` TEXT NOT NULL, `size` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, `mimeType` TEXT, `duration` INTEGER, PRIMARY KEY(`id`))"
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS `media_store_scan_state` (`id` INTEGER NOT NULL, `version` TEXT NOT NULL, PRIMARY KEY(`id`))")
        }
    }
