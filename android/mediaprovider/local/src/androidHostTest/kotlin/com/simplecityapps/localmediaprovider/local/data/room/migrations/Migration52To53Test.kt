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

private const val TEST_DB = "migration-52-53-test"

@RunWith(AndroidJUnit4::class)
class Migration52To53Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 52 to 53 keeps the pending favourites without their provider and external id`() {
        helper.createDatabase(TEST_DB, 52).apply {
            insertSong(id = 1, path = "jellyfin://item/a", mediaProvider = "Jellyfin")
            execSQL("INSERT INTO pending_favourites (songId, mediaProvider, externalId, favourite, changedAt) VALUES (1, 'Jellyfin', 'a', 1, 1234)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        migrated.query("SELECT songId, favourite, changedAt FROM pending_favourites").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getLong(0) shouldBe 1L
            cursor.getInt(1) shouldBe 1
            cursor.getLong(2) shouldBe 1234L
            cursor.count shouldBe 1
        }
        // Still deleted along with its song
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.execSQL("DELETE FROM songs WHERE id = 1")
        migrated.query("SELECT COUNT(*) FROM pending_favourites").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 0
        }
    }

    @Test
    fun `migrate 52 to 53 queues remote favourites made before the outbox, at the time they were made`() {
        helper.createDatabase(TEST_DB, 52).apply {
            insertSong(id = 1, path = "jellyfin://item/a", mediaProvider = "Jellyfin", favouritedAt = 1000)
            insertSong(id = 2, path = "emby://item/b", mediaProvider = "Emby", favouritedAt = 2000)
            insertSong(id = 3, path = "plex://item/c", mediaProvider = "Plex", favouritedAt = 3000)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        migrated.pendingFavourites() shouldBe listOf(Triple(1L, 1, 1000L), Triple(2L, 1, 2000L), Triple(3L, 1, 3000L))
    }

    @Test
    fun `migrate 52 to 53 doesn't queue local favourites or remote songs that aren't favourites`() {
        helper.createDatabase(TEST_DB, 52).apply {
            insertSong(id = 1, path = "/music/a.flac", mediaProvider = "Shuttle", favouritedAt = 1000)
            insertSong(id = 2, path = "content://media/b", mediaProvider = "MediaStore", favouritedAt = 2000)
            insertSong(id = 3, path = "jellyfin://item/c", mediaProvider = "Jellyfin", favouritedAt = null)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        migrated.pendingFavourites() shouldBe emptyList()
    }

    @Test
    fun `migrate 52 to 53 keeps a pending change rather than queueing the song's favourite over it`() {
        helper.createDatabase(TEST_DB, 52).apply {
            insertSong(id = 1, path = "jellyfin://item/a", mediaProvider = "Jellyfin", favouritedAt = 1000)
            execSQL("INSERT INTO pending_favourites (songId, mediaProvider, externalId, favourite, changedAt) VALUES (1, 'Jellyfin', 'a', 0, 5000)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 53, true, MIGRATION_52_53)

        migrated.pendingFavourites() shouldBe listOf(Triple(1L, 0, 5000L))
    }
}

private fun SupportSQLiteDatabase.insertSong(
    id: Long,
    path: String,
    mediaProvider: String,
    favouritedAt: Long? = null
) {
    execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, favouritedAt) " +
            "VALUES (?, 'Song', 1, 1, 180000, NULL, '', ?, 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, ?, NULL, NULL, NULL, NULL, NULL, NULL, ?)",
        arrayOf<Any?>(id, path, mediaProvider, favouritedAt)
    )
}

/** Each `pending_favourites` row as (songId, favourite, changedAt), by songId. */
private fun SupportSQLiteDatabase.pendingFavourites(): List<Triple<Long, Int, Long>> = query("SELECT songId, favourite, changedAt FROM pending_favourites ORDER BY songId").use { cursor ->
    buildList {
        while (cursor.moveToNext()) add(Triple(cursor.getLong(0), cursor.getInt(1), cursor.getLong(2)))
    }
}
