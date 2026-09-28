package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-48-49-test"

@RunWith(AndroidJUnit4::class)
class Migration48To49Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 48 to 49 backfills one play through per song that has been completed`() {
        helper.createDatabase(TEST_DB, 48).apply {
            insertSong(id = 1, path = "jellyfin://item/1", lastCompleted = "1790000000000")
            insertSong(id = 2, path = "jellyfin://item/2", lastCompleted = "NULL")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 49, true, MIGRATION_48_49)

        migrated.query("SELECT mediaProvider, songPath, startedAt, listenedMs, completed, localHour, weekday, contextType, contextId FROM play_events").use { cursor ->
            cursor.count shouldBe 1
            cursor.moveToFirst()
            cursor.getString(0) shouldBe "Jellyfin"
            cursor.getString(1) shouldBe "jellyfin://item/1"
            cursor.getLong(2) shouldBe 1_790_000_000_000
            cursor.getLong(3) shouldBe 180_000
            cursor.getInt(4) shouldBe 1
            (cursor.getInt(5) in 0..23) shouldBe true
            (cursor.getInt(6) in 1..7) shouldBe true
            cursor.getString(7) shouldBe "none"
            cursor.isNull(8) shouldBe true
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertSong(
        id: Long,
        path: String,
        lastCompleted: String
    ) {
        execSQL(
            "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
                "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
                "VALUES ($id, 'Song', 1, 1, 180000, NULL, '', '$path', 'Artist', 'Artist', 'Album', 5000, 'audio/mpeg', 0, 0, 1, NULL, $lastCompleted, 0, 'item-$id', 'Jellyfin')"
        )
    }
}
