package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-41-42-test"

@RunWith(AndroidJUnit4::class)
class Migration41To42Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 41 to 42 preserves song rows and leaves artworkVersion null until the next import`() {
        helper.createDatabase(TEST_DB, 41).apply {
            execSQL(
                "INSERT INTO songs (id, name, duration, genres, path, artists, size, mimeType, lastModified, " +
                    "playbackPosition, playCount, blacklisted, mediaProvider) " +
                    "VALUES (1, 'My Song', 1000, '', '/music/song.mp3', '', 0, 'audio/mpeg', 0, 0, 0, 0, 'Shuttle')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 42, true, MIGRATION_41_42)

        migrated.query("SELECT name, artworkVersion FROM songs WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("name")) shouldBe "My Song"
            cursor.isNull(cursor.getColumnIndexOrThrow("artworkVersion")) shouldBe true
        }
    }
}
