package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-40-41-test"

@RunWith(AndroidJUnit4::class)
class Migration40To41Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 40 to 41 preserves playlist rows and defaults sortDescending to false`() {
        helper.createDatabase(TEST_DB, 40).apply {
            execSQL(
                "INSERT INTO playlists (id, name, sortOrder, mediaProvider, externalId) " +
                    "VALUES (1, 'My Playlist', 'DEFAULT', 'Shuttle', NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 41, true, MIGRATION_40_41)

        migrated.query("SELECT name, sortDescending FROM playlists WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("name")) shouldBe "My Playlist"
            cursor.getInt(cursor.getColumnIndexOrThrow("sortDescending")) shouldBe 0
        }
    }
}
