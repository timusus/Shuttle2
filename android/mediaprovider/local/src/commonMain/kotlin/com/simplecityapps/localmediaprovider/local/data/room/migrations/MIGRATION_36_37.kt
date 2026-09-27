package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_36_37 =
    object : Migration(36, 37) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `playlist_song_join_2` (`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

            connection.execSQL(
                """
                INSERT INTO `playlist_song_join_2` (id, playlistId, songId, sortOrder) 
                SELECT id, playlistId, songId, id 
                FROM `playlist_song_join`
                """
            )

            connection.execSQL("DROP TABLE playlist_song_join")
            connection.execSQL("DROP INDEX IF EXISTS index_playlist_song_join_playlistId")
            connection.execSQL("DROP INDEX IF EXISTS index_playlist_song_join_songId")

            connection.execSQL("ALTER TABLE playlist_song_join_2 RENAME TO playlist_song_join")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_join_playlistId` ON `playlist_song_join` (`playlistId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_join_songId` ON `playlist_song_join` (`songId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_join_sort_order` ON `playlist_song_join` (`sortOrder`)")

            connection.execSQL("ALTER TABLE playlists ADD COLUMN sortOrder TEXT NOT NULL DEFAULT 'Position'")
        }
    }
