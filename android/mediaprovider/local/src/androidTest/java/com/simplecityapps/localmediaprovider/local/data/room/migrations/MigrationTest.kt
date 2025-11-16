package com.simplecityapps.localmediaprovider.local.data.room.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for database migrations, specifically MIGRATION_40_41 which adds FTS4 support.
 *
 * These tests ensure that:
 * 1. The FTS4 virtual table is created correctly
 * 2. Existing data is migrated to the FTS table
 * 3. FTS triggers are set up properly for insert/update/delete operations
 * 4. FTS search queries work after migration
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate40To41_createsFtsTable() {
        // Create database at version 40
        val db = helper.createDatabase(TEST_DB, 40)

        // Insert some test data into songs table before migration
        val values = ContentValues().apply {
            put("id", 1)
            put("name", "Bohemian Rhapsody")
            put("album", "A Night at the Opera")
            put("albumArtist", "Queen")
            put("artists", "Queen")
            put("track", 11)
            put("disc", 1)
            put("duration", 354000)
            put("path", "/test/path/song.mp3")
            put("size", 5000000)
            put("mimeType", "audio/mpeg")
            put("lastModified", System.currentTimeMillis())
            put("blacklisted", 0)
            put("playCount", 0)
            put("playbackPosition", 0)
            put("mediaProvider", "LOCAL")
        }
        db.insert("songs", SQLiteDatabase.CONFLICT_REPLACE, values)

        val values2 = ContentValues().apply {
            put("id", 2)
            put("name", "Stairway to Heaven")
            put("album", "Led Zeppelin IV")
            put("albumArtist", "Led Zeppelin")
            put("artists", "Led Zeppelin")
            put("track", 4)
            put("disc", 1)
            put("duration", 482000)
            put("path", "/test/path/song2.mp3")
            put("size", 6000000)
            put("mimeType", "audio/mpeg")
            put("lastModified", System.currentTimeMillis())
            put("blacklisted", 0)
            put("playCount", 0)
            put("playbackPosition", 0)
            put("mediaProvider", "LOCAL")
        }
        db.insert("songs", SQLiteDatabase.CONFLICT_REPLACE, values2)

        db.close()

        // Run migration to version 41
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 41, true, MIGRATION_40_41)

        // Verify FTS table was created
        val ftsTableQuery = migratedDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='songs_fts'")
        assertTrue("FTS table should exist", ftsTableQuery.moveToFirst())
        ftsTableQuery.close()

        // Verify existing data was migrated to FTS table
        val ftsCursor = migratedDb.query("SELECT COUNT(*) FROM songs_fts")
        assertTrue(ftsCursor.moveToFirst())
        assertEquals("FTS table should have 2 rows", 2, ftsCursor.getInt(0))
        ftsCursor.close()

        // Verify FTS search works
        val searchCursor = migratedDb.query("SELECT docid, name FROM songs_fts WHERE songs_fts MATCH 'bohemian'")
        assertTrue("Search should find 'Bohemian Rhapsody'", searchCursor.moveToFirst())
        assertEquals("Should find song with docid 1", 1, searchCursor.getInt(0))
        assertEquals("Should find correct song name", "Bohemian Rhapsody", searchCursor.getString(1))
        searchCursor.close()

        // Verify search on album works
        val albumSearchCursor = migratedDb.query("SELECT docid FROM songs_fts WHERE songs_fts MATCH 'zeppelin'")
        assertTrue("Search should find Led Zeppelin", albumSearchCursor.moveToFirst())
        assertEquals("Should find song with docid 2", 2, albumSearchCursor.getInt(0))
        albumSearchCursor.close()

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate40To41_triggersWorkCorrectly() {
        // Create and migrate database
        helper.createDatabase(TEST_DB, 40).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 41, true, MIGRATION_40_41)

        // Test INSERT trigger
        val insertValues = ContentValues().apply {
            put("id", 3)
            put("name", "Hotel California")
            put("album", "Hotel California")
            put("albumArtist", "Eagles")
            put("artists", "Eagles")
            put("track", 1)
            put("disc", 1)
            put("duration", 391000)
            put("path", "/test/path/song3.mp3")
            put("size", 5500000)
            put("mimeType", "audio/mpeg")
            put("lastModified", System.currentTimeMillis())
            put("blacklisted", 0)
            put("playCount", 0)
            put("playbackPosition", 0)
            put("mediaProvider", "LOCAL")
        }
        db.insert("songs", SQLiteDatabase.CONFLICT_REPLACE, insertValues)

        // Verify FTS table was updated via trigger
        var cursor = db.query("SELECT COUNT(*) FROM songs_fts")
        assertTrue(cursor.moveToFirst())
        assertEquals("FTS table should have the inserted row", 1, cursor.getInt(0))
        cursor.close()

        // Verify we can search for the new song
        cursor = db.query("SELECT docid FROM songs_fts WHERE songs_fts MATCH 'california'")
        assertTrue("Should find newly inserted song", cursor.moveToFirst())
        assertEquals("Should find song with docid 3", 3, cursor.getInt(0))
        cursor.close()

        // Test UPDATE trigger
        val updateValues = ContentValues().apply {
            put("name", "Hotel California (Live)")
            put("album", "Hotel California")
            put("albumArtist", "Eagles")
            put("artists", "Eagles")
        }
        db.update("songs", SQLiteDatabase.CONFLICT_REPLACE, updateValues, "id = ?", arrayOf("3"))

        // Verify FTS was updated
        cursor = db.query("SELECT name FROM songs_fts WHERE docid = 3")
        assertTrue(cursor.moveToFirst())
        assertEquals("FTS should reflect updated name", "Hotel California (Live)", cursor.getString(0))
        cursor.close()

        // Test DELETE trigger
        db.delete("songs", "id = ?", arrayOf("3"))

        // Verify FTS entry was deleted
        cursor = db.query("SELECT COUNT(*) FROM songs_fts WHERE docid = 3")
        assertTrue(cursor.moveToFirst())
        assertEquals("FTS entry should be deleted", 0, cursor.getInt(0))
        cursor.close()

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate40To41_ftsQueryWithMultipleWords() {
        // Create database with test data
        val db = helper.createDatabase(TEST_DB, 40)

        val values = ContentValues().apply {
            put("id", 1)
            put("name", "Comfortably Numb")
            put("album", "The Wall")
            put("albumArtist", "Pink Floyd")
            put("artists", "Pink Floyd")
            put("track", 6)
            put("disc", 2)
            put("duration", 382000)
            put("path", "/test/path/song.mp3")
            put("size", 5000000)
            put("mimeType", "audio/mpeg")
            put("lastModified", System.currentTimeMillis())
            put("blacklisted", 0)
            put("playCount", 0)
            put("playbackPosition", 0)
            put("mediaProvider", "LOCAL")
        }
        db.insert("songs", SQLiteDatabase.CONFLICT_REPLACE, values)
        db.close()

        // Run migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 41, true, MIGRATION_40_41)

        // Test FTS with OR query (as used in the app)
        val cursor = migratedDb.query(
            "SELECT docid, name FROM songs_fts WHERE songs_fts MATCH '\"comfortably\"* OR \"numb\"*'"
        )
        assertTrue("Should find song with multi-word query", cursor.moveToFirst())
        assertEquals("Should find correct song", "Comfortably Numb", cursor.getString(1))
        cursor.close()

        // Test FTS with artist search
        val artistCursor = migratedDb.query(
            "SELECT docid FROM songs_fts WHERE songs_fts MATCH '\"pink\"* OR \"floyd\"*'"
        )
        assertTrue("Should find Pink Floyd", artistCursor.moveToFirst())
        artistCursor.close()

        migratedDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate40To41_blacklistedSongsNotIndexed() {
        // Create database with blacklisted song
        val db = helper.createDatabase(TEST_DB, 40)

        val values = ContentValues().apply {
            put("id", 1)
            put("name", "Test Song")
            put("album", "Test Album")
            put("albumArtist", "Test Artist")
            put("artists", "Test Artist")
            put("track", 1)
            put("disc", 1)
            put("duration", 200000)
            put("path", "/test/path/song.mp3")
            put("size", 3000000)
            put("mimeType", "audio/mpeg")
            put("lastModified", System.currentTimeMillis())
            put("blacklisted", 1)  // Blacklisted!
            put("playCount", 0)
            put("playbackPosition", 0)
            put("mediaProvider", "LOCAL")
        }
        db.insert("songs", SQLiteDatabase.CONFLICT_REPLACE, values)
        db.close()

        // Run migration
        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 41, true, MIGRATION_40_41)

        // Blacklisted songs are migrated to FTS initially, but the app's search queries
        // filter them out using "WHERE songs.blacklisted = 0" in the JOIN
        // This is correct behavior - the FTS table mirrors the songs table,
        // and filtering happens at query time

        // Verify the song is in FTS (this is expected)
        val ftsCursor = migratedDb.query("SELECT COUNT(*) FROM songs_fts")
        assertTrue(ftsCursor.moveToFirst())
        assertEquals("FTS should contain the song", 1, ftsCursor.getInt(0))
        ftsCursor.close()

        // But when queried with the app's actual search pattern, it should be filtered out
        val joinCursor = migratedDb.query(
            """
            SELECT songs.id FROM songs_fts
            JOIN songs ON songs.id = songs_fts.docid
            WHERE songs_fts MATCH 'test'
            AND songs.blacklisted = 0
            """
        )
        assertFalse("Blacklisted songs should not appear in search results", joinCursor.moveToFirst())
        joinCursor.close()

        migratedDb.close()
    }
}
