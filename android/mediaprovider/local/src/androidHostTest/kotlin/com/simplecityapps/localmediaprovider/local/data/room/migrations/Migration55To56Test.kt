package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-55-56-test"

@RunWith(AndroidJUnit4::class)
class Migration55To56Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 55 to 56 adds an empty MediaStore listing and keeps the songs`() {
        helper.createDatabase(TEST_DB, 55).apply {
            execSQL(
                "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, lastCompleted, dateAdded) " +
                    "VALUES (1, 'Song', 1, 1, 180000, NULL, '', '/music/a.flac', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, 'Shuttle', NULL, NULL, NULL, NULL, NULL, NULL, 1000, 2000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 56, true, MIGRATION_55_56)

        migrated.query("SELECT COUNT(*) FROM media_store_files").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
        migrated.query("SELECT COUNT(*) FROM media_store_scan_state").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
        migrated.execSQL("INSERT INTO media_store_files (id, generation, path, displayName, size, lastModified, mimeType, duration) VALUES (7, 3, '/music/a.flac', 'a.flac', 10, 1000, NULL, NULL)")
        migrated.query("SELECT path FROM songs WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0) shouldBe "/music/a.flac"
        }
    }
}
