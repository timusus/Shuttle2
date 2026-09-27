package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * `songs.audioCodec` carries the source audio codec (e.g. `alac`, `flac`) reported by a song's provider, so a
 * container that can hold an undecodable codec (ALAC in `.m4a`) can be told apart from one that can't. Null for
 * every existing row until the next sync re-populates it.
 */
val MIGRATION_46_47 =
    object : Migration(46, 47) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `songs` ADD COLUMN `audioCodec` TEXT")
        }
    }
