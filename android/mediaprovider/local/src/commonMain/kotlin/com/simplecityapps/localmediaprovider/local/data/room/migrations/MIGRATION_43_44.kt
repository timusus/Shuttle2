package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * `songs.path` was unique across every provider, so the MediaStore and S2 (TagLib) providers fought over the same
 * file's row: whichever imported first kept it, the other's insert was silently dropped (#420). The unique index now
 * scopes to (path, mediaProvider), so each provider keeps its own row per file.
 */
val MIGRATION_43_44 =
    object : Migration(43, 44) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP INDEX IF EXISTS `index_songs_path`")
            connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_songs_path_mediaProvider` ON `songs` (`path`, `mediaProvider`)")
        }
    }
