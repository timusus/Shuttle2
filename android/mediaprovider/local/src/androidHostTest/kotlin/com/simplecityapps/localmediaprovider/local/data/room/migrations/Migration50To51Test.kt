package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-50-51-test"

@RunWith(AndroidJUnit4::class)
class Migration50To51Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 50 to 51 keeps the history and adds an empty resume_points table`() {
        helper.createDatabase(TEST_DB, 50).apply {
            execSQL(
                "INSERT INTO play_events (mediaProvider, songPath, startedAt, listenedMs, completed, localHour, weekday, contextType, contextId) " +
                    "VALUES ('Shuttle', '/music/a.flac', 1790000000000, 180000, 1, 9, 2, 'album', 'x')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 51, true, MIGRATION_50_51)

        migrated.query("SELECT COUNT(*) FROM play_events").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 1
        }
        migrated.execSQL(
            "INSERT INTO resume_points (contextType, contextId, mediaProvider, songPath, positionMs, track, trackCount, shuffled, finished, updatedAt) " +
                "VALUES ('album', 'x', 'Shuttle', '/music/a.flac', 61000, 4, 12, 0, 0, 1790000000000)"
        )
        migrated.query("SELECT songPath, track FROM resume_points WHERE contextType = 'album' AND contextId = 'x'").use { cursor ->
            cursor.count shouldBe 1
            cursor.moveToFirst()
            cursor.getString(0) shouldBe "/music/a.flac"
            cursor.getInt(1) shouldBe 4
        }
    }
}
