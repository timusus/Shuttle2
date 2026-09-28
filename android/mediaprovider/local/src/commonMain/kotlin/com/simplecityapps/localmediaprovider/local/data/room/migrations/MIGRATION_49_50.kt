package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The raw artist and album tags and ids of #637, as nullable columns on `songs`: the multi-value ALBUMARTISTS and
 * ARTISTS tags, the raw ARTIST string, COMPILATION, the MusicBrainz ids, and a server's album and artist ids. Lists are
 * ';'-joined like `artists`. Additive only, so every row keeps its id and everything keyed by it; the new columns stay
 * null until the one-off tag backfill reads each song again (`MediaImporter.backfillSongTags`).
 */
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
