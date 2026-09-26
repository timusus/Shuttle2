package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-46-47-test"

@RunWith(AndroidJUnit4::class)
class Migration46To47Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 46 to 47 adds a null audioCodec column`() {
        helper.createDatabase(TEST_DB, 46).apply {
            execSQL(
                "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
                    "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
                    "VALUES (1, 'Song', 1, 1, 180000, NULL, '', '/storage/emulated/0/Music/1.mp3', 'Artist', 'Artist', 'Album', 5000, 'audio/mpeg', 0, 0, 0, NULL, NULL, 0, NULL, 'Shuttle')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 47, true, MIGRATION_46_47)

        migrated.query("SELECT audioCodec FROM songs WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.isNull(0) shouldBe true
        }
    }
}
