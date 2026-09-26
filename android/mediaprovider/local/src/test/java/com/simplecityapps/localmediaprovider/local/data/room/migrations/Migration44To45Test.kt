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

private const val TEST_DB = "migration-44-45-test"

@RunWith(AndroidJUnit4::class)
class Migration44To45Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 44 to 45 backfills dateAdded from lastModified`() {
        helper.createDatabase(TEST_DB, 44).apply {
            insertSong(id = 1, path = "/storage/emulated/0/Music/One.mp3", lastModified = 1_700_000_000_000)
            insertSong(id = 2, path = "/storage/emulated/0/Music/Two.mp3", lastModified = 1_600_000_000_000)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 45, true, MIGRATION_44_45)

        migrated.query("SELECT id, dateAdded FROM songs ORDER BY id").use { cursor ->
            cursor.moveToNext() shouldBe true
            cursor.getLong(1) shouldBe 1_700_000_000_000
            cursor.moveToNext() shouldBe true
            cursor.getLong(1) shouldBe 1_600_000_000_000
        }
    }

    @Test
    fun `migrate 44 to 45 adds an empty smart playlists table`() {
        helper.createDatabase(TEST_DB, 44).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 45, true, MIGRATION_44_45)

        migrated.query("SELECT COUNT(*) FROM smart_playlists").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
        migrated.execSQL("INSERT INTO smart_playlists (name, rulesJson, createdAt) VALUES ('Never played', '{}', 1700000000000)")
        migrated.query("SELECT id, name FROM smart_playlists").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getLong(0) shouldBe 1
            cursor.getString(1) shouldBe "Never played"
        }
    }
}

private fun SupportSQLiteDatabase.insertSong(
    id: Long,
    path: String,
    lastModified: Long
) {
    execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
            "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
            "VALUES (?, 'Song', 1, 1, 180000, NULL, '', ?, 'Artist', 'Artist', 'Album', 5000, 'audio/mpeg', ?, 0, 0, NULL, NULL, 0, NULL, 'Shuttle')",
        arrayOf<Any?>(id, path, lastModified)
    )
}
