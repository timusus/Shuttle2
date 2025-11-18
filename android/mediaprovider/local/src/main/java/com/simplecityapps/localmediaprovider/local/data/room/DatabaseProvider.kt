package com.simplecityapps.localmediaprovider.local.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplecityapps.localmediaprovider.BuildConfig
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_23_24
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_24_25
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_25_26
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_26_27
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_27_28
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_28_29
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_29_30
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_30_31
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_31_32
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_32_33
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_33_34
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_34_35
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_35_36
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_36_37
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_37_38
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_38_39
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_39_40
import com.simplecityapps.localmediaprovider.local.data.room.migrations.MIGRATION_40_41

class DatabaseProvider(
    private val context: Context
) {
    val database: MediaDatabase by lazy {
        Room.databaseBuilder(context, MediaDatabase::class.java, "song.db")
            .addMigrations(
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                MIGRATION_36_37,
                MIGRATION_37_38,
                MIGRATION_38_39,
                MIGRATION_39_40,
                MIGRATION_40_41
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Create FTS table when database is created from scratch
                    // This mirrors what happens in MIGRATION_40_41
                    createFtsTable(db)
                }
            })
            .apply {
                if (!BuildConfig.DEBUG) {
                    fallbackToDestructiveMigration()
                }
            }
            .build()
    }

    private fun createFtsTable(db: SupportSQLiteDatabase) {
        // Create FTS4 virtual table
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

        // Populate FTS table (will be empty on fresh install, populated as songs are added)
        db.execSQL(
            """
            INSERT INTO songs_fts(docid, name, album, albumArtist, artists)
            SELECT id, name, album, albumArtist, artists FROM songs
            """.trimIndent()
        )

        // Create triggers to keep FTS table in sync
        db.execSQL(
            """
            CREATE TRIGGER songs_fts_insert AFTER INSERT ON songs BEGIN
                INSERT INTO songs_fts(docid, name, album, albumArtist, artists)
                VALUES (new.id, new.name, new.album, new.albumArtist, new.artists);
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER songs_fts_delete AFTER DELETE ON songs BEGIN
                DELETE FROM songs_fts WHERE docid = old.id;
            END
            """.trimIndent()
        )

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
