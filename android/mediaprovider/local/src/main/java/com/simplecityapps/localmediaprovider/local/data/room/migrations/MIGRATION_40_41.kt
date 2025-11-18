package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_40_41 =
    object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create FTS4 virtual table for full-text search on songs
            // FTS4 is more widely supported than FTS5 across Android versions
            // This indexes name, album, albumArtist, and artists for fast text search
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS songs_fts USING fts4(
                    name,
                    album,
                    albumArtist,
                    artists,
                    content=songs
                )
                """.trimIndent()
            )

            // Populate the FTS table with existing data
            // FTS4 uses docid instead of rowid for content table linking
            db.execSQL(
                """
                INSERT INTO songs_fts(docid, name, album, albumArtist, artists)
                SELECT id, name, album, albumArtist, artists FROM songs
                """.trimIndent()
            )

            // Create triggers to keep FTS table in sync with songs table

            // Trigger: After insert on songs, insert into FTS
            // FTS4 uses docid for the row identifier
            db.execSQL(
                """
                CREATE TRIGGER songs_fts_insert AFTER INSERT ON songs BEGIN
                    INSERT INTO songs_fts(docid, name, album, albumArtist, artists)
                    VALUES (new.id, new.name, new.album, new.albumArtist, new.artists);
                END
                """.trimIndent()
            )

            // Trigger: After delete on songs, delete from FTS
            // FTS4 uses DELETE command syntax
            db.execSQL(
                """
                CREATE TRIGGER songs_fts_delete AFTER DELETE ON songs BEGIN
                    DELETE FROM songs_fts WHERE docid = old.id;
                END
                """.trimIndent()
            )

            // Trigger: After update on songs, update FTS
            // FTS4: delete old entry and insert new one
            db.execSQL(
                """
                CREATE TRIGGER songs_fts_update AFTER UPDATE ON songs BEGIN
                    DELETE FROM songs_fts WHERE docid = old.id;
                    INSERT INTO songs_fts(docid, name, album, albumArtist, artists)
                    VALUES (new.id, new.name, new.album, new.albumArtist, new.artists);
                END
                """.trimIndent()
            )
        }
    }
