package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS songs2 (`name` TEXT," +
                    " `track` INTEGER," +
                    " `disc` INTEGER," +
                    " `duration` INTEGER NOT NULL," +
                    " `year` INTEGER," +
                    " `genres` TEXT NOT NULL," +
                    " `path` TEXT NOT NULL," +
                    " `albumArtist` TEXT," +
                    " `artists` TEXT NOT NULL," +
                    " `album` TEXT," +
                    " `size` INTEGER NOT NULL," +
                    " `mimeType` TEXT NOT NULL," +
                    " `lastModified` INTEGER NOT NULL," +
                    " `playbackPosition` INTEGER NOT NULL," +
                    " `playCount` INTEGER NOT NULL," +
                    " `lastPlayed` INTEGER," +
                    " `lastCompleted` INTEGER," +
                    " `blacklisted` INTEGER NOT NULL," +
                    " `externalId` TEXT," +
                    " `mediaProvider` TEXT NOT NULL," +
                    " `replayGainTrack` REAL," +
                    " `replayGainAlbum` REAL," +
                    " `lyrics` TEXT," +
                    " `grouping` TEXT," +
                    " `bitRate` INTEGER," +
                    " `bitDepth` INTEGER," +
                    " `sampleRate` INTEGER," +
                    " `channelCount` INTEGER," +
                    " `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
        database.execSQL("DROP INDEX IF EXISTS index_songs_path")
        database.execSQL("DROP INDEX IF EXISTS index_songs2_path")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_songs_path ON songs2 (path)")
        database.execSQL(
            "INSERT INTO songs2 (id, " +
                    "name, " +
                    "track, " +
                    "disc, " +
                    "duration, " +
                    "year, " +
                    "path, " +
                    "albumArtist, " +
                    "artists, " +
                    "album, " +
                    "size, " +
                    "mimeType, " +
                    "genres, " +
                    "lastModified, " +
                    "playbackPosition, " +
                    "playCount, " +
                    "lastPlayed, " +
                    "lastCompleted, " +
                    "blacklisted, " +
                    "externalId, " +
                    "mediaProvider, " +
                    "replayGainTrack, " +
                    "replayGainAlbum, " +
                    "lyrics, " +
                    "grouping, " +
                    "bitRate, " +
                    "sampleRate, " +
                    "channelCount) " +
                    "SELECT songs.id, " +
                    "songs.name, " +
                    "track, " +
                    "disc, " +
                    "duration, " +
                    "year, " +
                    "path, " +
                    "albumArtist, " +
                    "'', " +
                    "album, " +
                    "size, " +
                    "mimeType, " +
                    "genres, " +
                    "lastModified, " +
                    "playbackPosition, " +
                    "playCount, " +
                    "lastPlayed, " +
                    "lastCompleted, " +
                    "blacklisted, " +
                    "cast(mediaStoreId as text), " +
                    "mediaProvider, " +
                    "replayGainTrack, " +
                    "replayGainAlbum, " +
                    "lyrics, " +
                    "grouping, " +
                    "bitRate, " +
                    "sampleRate, " +
                    "channelCount " +
                    "FROM songs"
        )
        database.execSQL("DROP TABLE songs")
        database.execSQL("ALTER TABLE songs2 RENAME TO songs")
    }
}