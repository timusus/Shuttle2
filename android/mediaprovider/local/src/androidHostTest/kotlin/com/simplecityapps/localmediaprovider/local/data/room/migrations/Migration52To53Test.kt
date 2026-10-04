package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-52-53-test"

@RunWith(AndroidJUnit4::class)
class Migration52To53Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 52 to 53 keeps the pending favourites without their provider and external id`() {
        helper.createDatabase(TEST_DB, 52).apply {
            execSQL(
                "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount) " +
                    "VALUES (1, 'Song', 1, 1, 180000, NULL, '', 'jellyfin://item/a', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, 'Jellyfin', NULL, NULL, NULL, NULL, NULL, NULL)"
            )
            execSQL("INSERT INTO pending_favourites (songId, mediaProvider, externalId, favourite, changedAt) VALUES (1, 'Jellyfin', 'a', 1, 1234)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        migrated.query("SELECT songId, favourite, changedAt FROM pending_favourites").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getLong(0) shouldBe 1L
            cursor.getInt(1) shouldBe 1
            cursor.getLong(2) shouldBe 1234L
            cursor.count shouldBe 1
        }
        // Still deleted along with its song
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL("DELETE FROM songs WHERE id = 1")
        migrated.query("SELECT COUNT(*) FROM pending_favourites").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 0
        }
    }
}
