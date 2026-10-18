package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds nullable raw tag and id columns; additive, so row ids stay and the one-off tag backfill fills them in. */
val MIGRATION_49_50 =
    object : Migration(49, 50) {
        override fun migrate(connection: SQLiteConnection) {
            listOf(
                "albumArtists" to "TEXT",
                "artistsTag" to "TEXT",
                "artistDisplay" to "TEXT",
                "compilation" to "INTEGER",
                "mbTrackId" to "TEXT",
                "mbAlbumId" to "TEXT",
                "mbReleaseGroupId" to "TEXT",
                "mbArtistIds" to "TEXT",
                "mbAlbumArtistIds" to "TEXT",
                "serverAlbumId" to "TEXT",
                "serverArtistIds" to "TEXT",
                "serverAlbumArtistIds" to "TEXT"
            ).forEach { (column, type) ->
                connection.execSQL("ALTER TABLE `songs` ADD COLUMN `$column` $type")
            }
        }
    }
