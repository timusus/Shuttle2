package com.simplecityapps.localmediaprovider.local.data.room.migrations

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-43-44-test"

@RunWith(AndroidJUnit4::class)
class Migration43To44Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 43 to 44 keeps existing songs and scopes the path index to the provider`() {
        helper.createDatabase(TEST_DB, 43).apply {
            insertSong(id = 1, path = "/storage/emulated/0/Music/Song.mp3", mediaProvider = "Shuttle")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 44, true, MIGRATION_43_44)

        migrated.query("SELECT path FROM songs WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0) shouldBe "/storage/emulated/0/Music/Song.mp3"
        }

        // The same path is now fine for a different provider...
        migrated.insertSong(id = 2, path = "/storage/emulated/0/Music/Song.mp3", mediaProvider = "MediaStore")
        migrated.query("SELECT COUNT(*) FROM songs").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 2
        }

        // ...but still a conflict within the same provider
        shouldThrow<SQLiteConstraintException> {
            migrated.insertSong(id = 3, path = "/storage/emulated/0/Music/Song.mp3", mediaProvider = "Shuttle")
        }
    }
}

private fun SupportSQLiteDatabase.insertSong(
    id: Long,
    path: String,
    mediaProvider: String
) {
    execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
            "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
            "VALUES (?, 'Song', 1, 1, 180000, NULL, '', ?, 'Artist', 'Artist', 'Album', 5000, 'audio/mpeg', 1700000000000, 0, 0, NULL, NULL, 0, NULL, ?)",
        arrayOf<Any?>(id, path, mediaProvider)
    )
}
