package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-54-55-test"

@RunWith(AndroidJUnit4::class)
class Migration54To55Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 54 to 55 indexes songs by last completed and date added and keeps the songs`() {
        helper.createDatabase(TEST_DB, 54).apply {
            execSQL(
                "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, lastCompleted, dateAdded) " +
                    "VALUES (1, 'Song', 1, 1, 180000, NULL, '', '/music/a.flac', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, 'Shuttle', NULL, NULL, NULL, NULL, NULL, NULL, 1000, 2000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 55, true, MIGRATION_54_55)

        val indices =
            migrated.query("PRAGMA index_list(`songs`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
        indices shouldContainAll listOf("index_songs_lastCompleted", "index_songs_dateAdded")
        migrated.query("SELECT lastCompleted, dateAdded FROM songs").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getLong(0) shouldBe 1000L
            cursor.getLong(1) shouldBe 2000L
        }
    }
}
