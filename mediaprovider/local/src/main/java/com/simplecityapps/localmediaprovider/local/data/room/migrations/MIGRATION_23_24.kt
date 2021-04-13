package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val favoritesName = "Favorites"

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        database.execSQL("CREATE TABLE IF NOT EXISTS `playlist_song_join` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX `index_playlist_song_join_songId` ON `playlist_song_join` (`songId`)")
        database.execSQL("CREATE INDEX `index_playlist_song_join_playlistId` ON `playlist_song_join` (`playlistId`)")
        database.execSQL("INSERT INTO playlists (name) VALUES('$favoritesName')")
    }
}