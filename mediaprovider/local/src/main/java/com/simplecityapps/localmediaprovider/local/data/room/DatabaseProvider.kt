package com.simplecityapps.localmediaprovider.local.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Named

class DatabaseProvider constructor(
    private val context: Context,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope
) {

    val database: MediaDatabase by lazy {
        Room.databaseBuilder(context, MediaDatabase::class.java, "song.db")
            .addMigrations(MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29)
            .addCallback(callback)
            .build()
    }

    private val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            appCoroutineScope.launch {
                database.playlistDataDao().insert(PlaylistData(name = favoritesName))
            }
        }
    }

    private val favoritesName: String = "Favorites"

    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `playlist_song_join` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX `index_playlist_song_join_songId` ON `playlist_song_join` (`songId`)")
            database.execSQL("CREATE INDEX `index_playlist_song_join_playlistId` ON `playlist_song_join` (`playlistId`)")
            database.execSQL("INSERT INTO playlists (name) VALUES('$favoritesName')")
        }
    }

    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `songs` ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT 'audio/*'")
        }
    }

    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `playlists` ADD COLUMN `media_store_id` INTEGER")
        }
    }

    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `songs` ADD COLUMN `blacklisted` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `songs` ADD COLUMN `mediaStoreId` INTEGER")
        }
    }

    private val MIGRATION_28_29 = object : Migration(28, 29) {
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
}