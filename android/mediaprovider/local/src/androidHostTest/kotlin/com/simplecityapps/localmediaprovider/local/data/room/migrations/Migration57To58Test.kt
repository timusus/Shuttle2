package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-57-58-test"

@RunWith(AndroidJUnit4::class)
class Migration57To58Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 57 to 58 adds the artwork item id, empty for the songs already stored`() {
        helper.createDatabase(TEST_DB, 57).apply {
            insertSong(1, "jellyfin://item/a", "Jellyfin")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 58, true, MIGRATION_57_58)

        migrated.query("SELECT path, artworkItemId FROM songs WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0) shouldBe "jellyfin://item/a"
            cursor.isNull(1) shouldBe true
        }
    }

    private fun SupportSQLiteDatabase.insertSong(
        id: Long,
        path: String,
        provider: String
    ) = execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, lastCompleted, dateAdded) " +
            "VALUES ($id, 'Song', 1, 1, 180000, NULL, '', '$path', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, '$provider', NULL, NULL, NULL, NULL, NULL, NULL, 1000, 2000)"
    )
}
