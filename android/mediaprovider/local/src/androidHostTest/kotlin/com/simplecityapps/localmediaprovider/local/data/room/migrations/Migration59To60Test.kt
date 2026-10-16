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

private const val TEST_DB = "migration-59-60-test"

@RunWith(AndroidJUnit4::class)
class Migration59To60Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 59 to 60 adds the raw album artist column, empty for the songs already stored`() {
        helper.createDatabase(TEST_DB, 59).apply {
            insertSong(1, "/music/a.flac")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 60, true, MIGRATION_59_60)

        migrated.query("SELECT path, albumArtistDisplay FROM songs WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0) shouldBe "/music/a.flac"
            cursor.isNull(1) shouldBe true
        }
    }

    private fun SupportSQLiteDatabase.insertSong(
        id: Long,
        path: String
    ) = execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, lastCompleted, dateAdded) " +
            "VALUES ($id, 'Song', 1, 1, 180000, NULL, '', '$path', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, 'Shuttle', NULL, NULL, NULL, NULL, NULL, NULL, 1000, 2000)"
    )
}
