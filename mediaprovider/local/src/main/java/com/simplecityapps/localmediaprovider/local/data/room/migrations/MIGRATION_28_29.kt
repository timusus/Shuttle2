package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS songs2 (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, track INTEGER NOT NULL, disc INTEGER NOT NULL, duration INTEGER NOT NULL, year INTEGER NOT NULL, path TEXT NOT NULL, albumArtist TEXT NOT NULL, album TEXT NOT NULL, size INTEGER NOT NULL, mimeType TEXT NOT NULL, lastModified INTEGER NOT NULL, playbackPosition INTEGER NOT NULL, playCount INTEGER NOT NULL, lastPlayed INTEGER, lastCompleted INTEGER, blacklisted INTEGER NOT NULL, mediaStoreId INTEGER)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_songs2_path ON songs2 (path)")
        database.execSQL("INSERT INTO songs2 (id, name, track, disc, duration, year, path, albumArtist, album, size, mimeType, lastModified, playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, mediaStoreId) SELECT songs.id, songs.name, track, disc, duration, year, path, album_artists.name as albumArtist, albums.name as album, size, mimeType, lastModified, playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, mediaStoreId FROM songs LEFT JOIN album_artists ON album_artists.id = songs.albumArtistId LEFT JOIN albums ON albums.id = songs.albumId")
        database.execSQL("DROP TABLE songs")
        database.execSQL("ALTER TABLE songs2 RENAME TO songs")
        database.execSQL("DROP TABLE album_artists")
        database.execSQL("DROP TABLE albums")
        database.execSQL("CREATE VIEW `AlbumData` AS SELECT songs.album as name, songs.albumArtist as albumArtist, count(distinct songs.id) as songCount, sum(distinct songs.duration) as duration, min(distinct songs.year) as year, min(distinct songs.playCount) as playCount FROM songs WHERE songs.blacklisted == 0 GROUP BY LOWER(songs.albumArtist), LOWER(songs.album) ORDER BY name")
        database.execSQL("CREATE VIEW `AlbumArtistData` AS SELECT songs.albumArtist as name, count(distinct songs.album) as albumCount, count(distinct songs.id) as songCount, min(distinct songs.playCount) as playCount FROM songs WHERE songs.blacklisted == 0 GROUP BY LOWER(songs.albumArtist) ORDER BY name")
    }
}