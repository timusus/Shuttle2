package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-51-52-test"

@RunWith(AndroidJUnit4::class)
class Migration51To52Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 51 to 52 keeps the songs and adds an empty identity_generation table`() {
        helper.createDatabase(TEST_DB, 51).apply {
            execSQL(
                "INSERT INTO songs (name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount) " +
                    "VALUES ('Song', 1, 1, 180000, NULL, '', '/music/a.flac', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, 'Shuttle', NULL, NULL, NULL, NULL, NULL, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 52, true, MIGRATION_51_52)

        migrated.query("SELECT COUNT(*) FROM songs").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 1
        }
        // Its row and the triggers that bump it come with IdentityGenerationTriggers on open
        migrated.query("SELECT COUNT(*) FROM identity_generation").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 0
        }
    }
}
