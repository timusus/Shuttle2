package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-56-57-test"

@RunWith(AndroidJUnit4::class)
class Migration56To57Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 56 to 57 copies the listing to each local provider with songs`() {
        helper.createDatabase(TEST_DB, 56).apply {
            insertSong(1, "/music/a.flac", "Shuttle")
            insertSong(2, "/music/b.flac", "MediaStore")
            insertSong(3, "https://jellyfin/b", "Jellyfin")
            execSQL("INSERT INTO media_store_files (id, generation, path, displayName, size, lastModified, mimeType, duration) VALUES (7, 3, '/music/a.flac', 'a.flac', 10, 1000, 'audio/flac', 185000)")
            execSQL("INSERT INTO media_store_scan_state (id, version) VALUES (0, 'v1')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57)

        migrated.rows("SELECT provider, id, generation, path, duration FROM media_store_files") shouldContainExactlyInAnyOrder
            listOf("Shuttle|7|3|/music/a.flac|185000", "MediaStore|7|3|/music/a.flac|185000")
        migrated.rows("SELECT provider, version FROM media_store_scan_state") shouldContainExactlyInAnyOrder listOf("Shuttle|v1", "MediaStore|v1")
        migrated.rows("SELECT path FROM songs WHERE id = 1") shouldBe listOf("/music/a.flac")
    }

    @Test
    fun `migrate 56 to 57 keeps no listing without local songs`() {
        helper.createDatabase(TEST_DB, 56).apply {
            execSQL("INSERT INTO media_store_files (id, generation, path, displayName, size, lastModified, mimeType, duration) VALUES (7, 3, '/music/a.flac', 'a.flac', 10, 1000, NULL, NULL)")
            execSQL("INSERT INTO media_store_scan_state (id, version) VALUES (0, 'v1')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57)

        migrated.rows("SELECT provider FROM media_store_files") shouldBe emptyList()
        migrated.rows("SELECT provider FROM media_store_scan_state") shouldBe emptyList()
    }

    private fun SupportSQLiteDatabase.insertSong(
        id: Long,
        path: String,
        provider: String
    ) = execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, lastCompleted, dateAdded) " +
            "VALUES ($id, 'Song', 1, 1, 180000, NULL, '', '$path', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, '$provider', NULL, NULL, NULL, NULL, NULL, NULL, 1000, 2000)"
    )

    private fun SupportSQLiteDatabase.rows(sql: String): List<String> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { column -> cursor.getString(column) })
        }
    }
}
