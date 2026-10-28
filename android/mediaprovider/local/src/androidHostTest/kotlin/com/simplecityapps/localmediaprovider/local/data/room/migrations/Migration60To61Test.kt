package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-60-61-test"

@RunWith(AndroidJUnit4::class)
class Migration60To61Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 60 to 61 keeps play stats and adds an empty library_generation table`() {
        helper.createDatabase(TEST_DB, 60).apply {
            execSQL(
                "INSERT INTO songs (name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, lastPlayed, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, favouritedAt) " +
                    "VALUES ('Song', 1, 1, 180000, NULL, '', '/music/a.flac', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 42000, 7, 1700000000000, 0, 'Shuttle', NULL, NULL, NULL, NULL, NULL, NULL, 1600000000000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 61, true, MIGRATION_60_61)

        migrated.query("SELECT playCount, playbackPosition, lastPlayed, favouritedAt FROM songs").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 7
            cursor.getInt(1) shouldBe 42000
            cursor.getLong(2) shouldBe 1700000000000
            cursor.getLong(3) shouldBe 1600000000000
        }
        // Its row and the triggers that bump it come with IdentityGenerationTriggers on open
        migrated.query("SELECT COUNT(*) FROM library_generation").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 0
        }
    }
}
