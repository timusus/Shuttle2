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

private const val TEST_DB = "migration-49-50-test"

private val NEW_COLUMNS =
    listOf(
        "albumArtists", "artistsTag", "artistDisplay", "compilation", "mbTrackId", "mbAlbumId", "mbReleaseGroupId",
        "mbArtistIds", "mbAlbumArtistIds", "serverAlbumId", "serverArtistIds", "serverAlbumArtistIds"
    )

@RunWith(AndroidJUnit4::class)
class Migration49To50Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 49 to 50 keeps every song, its playlists and its history, with the new tag columns null`() {
        helper.createDatabase(TEST_DB, 49).apply {
            insertSong(id = 7, path = "/music/a.flac", provider = "Shuttle", playCount = 3, favouritedAt = "1790000000000")
            insertSong(id = 12, path = "jellyfin://item/b", provider = "Jellyfin", playCount = 0, favouritedAt = "NULL")
            execSQL("INSERT INTO playlists (id, name, sortOrder, sortDescending, mediaProvider, externalId) VALUES (1, 'Mix', 'Position', 0, 'Shuttle', NULL)")
            execSQL("INSERT INTO playlist_song_join (playlistId, songId, sortOrder) VALUES (1, 12, 0), (1, 7, 1)")
            execSQL(
                "INSERT INTO play_events (mediaProvider, songPath, startedAt, listenedMs, completed, localHour, weekday, contextType, contextId) " +
                    "VALUES ('Shuttle', '/music/a.flac', 1790000000000, 180000, 1, 9, 2, 'album', 'x')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 50, true, MIGRATION_49_50)

        migrated.query("SELECT id, path, artists, albumArtist, playCount, favouritedAt, ${NEW_COLUMNS.joinToString()} FROM songs ORDER BY id").use { cursor ->
            cursor.count shouldBe 2
            cursor.moveToFirst()
            cursor.getLong(0) shouldBe 7
            cursor.getString(1) shouldBe "/music/a.flac"
            cursor.getString(2) shouldBe "Artist A;Artist B"
            cursor.getString(3) shouldBe "Album Artist"
            cursor.getInt(4) shouldBe 3
            cursor.getLong(5) shouldBe 1_790_000_000_000
            NEW_COLUMNS.indices.forEach { index -> cursor.isNull(6 + index) shouldBe true }
            cursor.moveToNext()
            cursor.getLong(0) shouldBe 12
            NEW_COLUMNS.indices.forEach { index -> cursor.isNull(6 + index) shouldBe true }
        }
        migrated.query("SELECT songId FROM playlist_song_join WHERE playlistId = 1 ORDER BY sortOrder").use { cursor ->
            cursor.count shouldBe 2
            cursor.moveToFirst()
            cursor.getLong(0) shouldBe 12
            cursor.moveToNext()
            cursor.getLong(0) shouldBe 7
        }
        migrated.query("SELECT songPath, startedAt, contextType FROM play_events").use { cursor ->
            cursor.count shouldBe 1
            cursor.moveToFirst()
            cursor.getString(0) shouldBe "/music/a.flac"
            cursor.getLong(1) shouldBe 1_790_000_000_000
            cursor.getString(2) shouldBe "album"
        }
    }

    private fun SupportSQLiteDatabase.insertSong(
        id: Long,
        path: String,
        provider: String,
        playCount: Int,
        favouritedAt: String
    ) {
        execSQL(
            "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
                "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider, dateAdded, favouritedAt) " +
                "VALUES ($id, 'Song', 1, 1, 180000, 2020, 'Rock', '$path', 'Album Artist', 'Artist A;Artist B', 'Album', 5000, 'audio/flac', 0, 0, " +
                "$playCount, NULL, NULL, 0, NULL, '$provider', 0, $favouritedAt)"
        )
    }
}
