package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-42-43-test"

@RunWith(AndroidJUnit4::class)
class Migration42To43Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 42 to 43 keeps playlists and adds an empty pinned_collections table`() {
        helper.createDatabase(TEST_DB, 42).apply {
            execSQL(
                "INSERT INTO playlists (id, name, sortOrder, sortDescending, mediaProvider, externalId) " +
                    "VALUES (1, 'My Playlist', 'DEFAULT', 0, 'Jellyfin', 'abc')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 43, true, MIGRATION_42_43)

        migrated.query("SELECT name FROM playlists WHERE id = 1").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0) shouldBe "My Playlist"
        }
        migrated.query("SELECT COUNT(*) FROM pinned_collections").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
    }

    @Test
    fun `pinned_collections keeps one row per collection and provider`() {
        helper.createDatabase(TEST_DB, 42).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 43, true, MIGRATION_42_43)

        migrated.execSQL("INSERT INTO pinned_collections VALUES ('Playlist', '1', 'Jellyfin')")
        migrated.execSQL("INSERT INTO pinned_collections VALUES ('Playlist', '1', 'Emby')")
        migrated.execSQL("INSERT INTO pinned_collections VALUES ('Album', '1', 'Jellyfin')")
        migrated.execSQL("INSERT OR IGNORE INTO pinned_collections VALUES ('Playlist', '1', 'Jellyfin')")

        migrated.query("SELECT COUNT(*) FROM pinned_collections").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 3
        }
    }
}
