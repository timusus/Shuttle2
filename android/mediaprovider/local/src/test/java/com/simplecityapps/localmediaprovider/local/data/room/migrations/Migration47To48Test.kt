package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-47-48-test"

@RunWith(AndroidJUnit4::class)
class Migration47To48Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 47 to 48 adds an empty pending favourites table`() {
        helper.createDatabase(TEST_DB, 47).apply {
            execSQL(
                "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
                    "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
                    "VALUES (1, 'Song', 1, 1, 180000, NULL, '', '/storage/emulated/0/Music/1.mp3', 'Artist', 'Artist', 'Album', 5000, 'audio/mpeg', 0, 0, 0, NULL, NULL, 0, 'item-1', 'Jellyfin')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 48, true, MIGRATION_47_48)

        migrated.query("SELECT COUNT(*) FROM pending_favourites").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
    }

    @Test
    fun `a pending favourite row is deleted when its song is deleted`() {
        helper.createDatabase(TEST_DB, 47).apply {
            execSQL(
                "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
                    "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
                    "VALUES (1, 'Song', 1, 1, 180000, NULL, '', '/storage/emulated/0/Music/1.mp3', 'Artist', 'Artist', 'Album', 5000, 'audio/mpeg', 0, 0, 0, NULL, NULL, 0, 'item-1', 'Jellyfin')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 48, true, MIGRATION_47_48)
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL("INSERT INTO pending_favourites (songId, mediaProvider, externalId, favourite, changedAt) VALUES (1, 'Jellyfin', 'item-1', 1, 0)")
        migrated.execSQL("DELETE FROM songs WHERE id = 1")

        migrated.query("SELECT COUNT(*) FROM pending_favourites").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
    }
}
