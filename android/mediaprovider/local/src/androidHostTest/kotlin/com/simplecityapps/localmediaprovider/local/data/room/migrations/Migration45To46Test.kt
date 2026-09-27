package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-45-46-test"

@RunWith(AndroidJUnit4::class)
class Migration45To46Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 45 to 46 makes the songs of the Favorites playlists favourites, the latest entry newest`() {
        helper.createDatabase(TEST_DB, 45).apply {
            (1L..5L).forEach { id -> insertSong(id) }
            insertPlaylist(id = 1, name = "Favorites")
            // Created under a German locale (#528)
            insertPlaylist(id = 2, name = "Favoriten")
            insertEntries(playlistId = 1, songIds = listOf(3, 1))
            insertEntries(playlistId = 2, songIds = listOf(2, 3))
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 46, true, MIGRATION_45_46)

        val favouritedAt = migrated.favouritedAt()
        favouritedAt.getValue(4) shouldBe null
        favouritedAt.getValue(5) shouldBe null
        // Playlist 1 then 2, each in its own order: 1, 2, then 3 (its later entry), so 3 is the most recent
        favouritedAt.getValue(3)!! shouldBeGreaterThan favouritedAt.getValue(2)!!
        favouritedAt.getValue(2)!! shouldBeGreaterThan favouritedAt.getValue(1)!!
    }

    @Test
    fun `migrate 45 to 46 deletes the Favorites playlists but keeps the rest`() {
        helper.createDatabase(TEST_DB, 45).apply {
            (1L..3L).forEach { id -> insertSong(id) }
            insertPlaylist(id = 1, name = "Favorites")
            insertPlaylist(id = 2, name = "Road trip")
            // An m3u file and a media server playlist that happen to share the name are the user's own playlists
            insertPlaylist(id = 3, name = "Favorites", externalId = "content://tree/Favorites.m3u")
            insertPlaylist(id = 4, name = "Favorites", mediaProvider = "Jellyfin", externalId = "abc")
            insertEntries(playlistId = 1, songIds = listOf(1))
            insertEntries(playlistId = 2, songIds = listOf(1, 2))
            insertEntries(playlistId = 3, songIds = listOf(3))
            insertEntries(playlistId = 4, songIds = listOf(3))
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 46, true, MIGRATION_45_46)

        migrated.longs("SELECT id FROM playlists ORDER BY id") shouldContainExactly listOf(2L, 3L, 4L)
        migrated.longs("SELECT playlistId FROM playlist_song_join ORDER BY id") shouldContainExactly listOf(2L, 2L, 3L, 4L)
        val favouritedAt = migrated.favouritedAt()
        (favouritedAt.getValue(1) != null) shouldBe true
        favouritedAt.getValue(2) shouldBe null
        favouritedAt.getValue(3) shouldBe null
    }

    @Test
    fun `migrate 45 to 46 without a Favorites playlist favourites nothing`() {
        helper.createDatabase(TEST_DB, 45).apply {
            insertSong(1)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 46, true, MIGRATION_45_46)

        migrated.favouritedAt() shouldBe mapOf(1L to null)
    }
}

private fun SupportSQLiteDatabase.insertSong(id: Long) {
    execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
            "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider) " +
            "VALUES (?, 'Song', 1, 1, 180000, NULL, '', ?, 'Artist', 'Artist', 'Album', 5000, 'audio/mpeg', 0, 0, 0, NULL, NULL, 0, NULL, 'Shuttle')",
        arrayOf<Any?>(id, "/storage/emulated/0/Music/$id.mp3")
    )
}

private fun SupportSQLiteDatabase.insertPlaylist(
    id: Long,
    name: String,
    mediaProvider: String = "Shuttle",
    externalId: String? = null
) {
    execSQL(
        "INSERT INTO playlists (id, name, sortOrder, mediaProvider, externalId) VALUES (?, ?, 'Position', ?, ?)",
        arrayOf<Any?>(id, name, mediaProvider, externalId)
    )
}

private fun SupportSQLiteDatabase.insertEntries(
    playlistId: Long,
    songIds: List<Long>
) {
    songIds.forEachIndexed { index, songId ->
        execSQL("INSERT INTO playlist_song_join (playlistId, songId, sortOrder) VALUES (?, ?, ?)", arrayOf<Any?>(playlistId, songId, index))
    }
}

private fun SupportSQLiteDatabase.favouritedAt(): Map<Long, Long?> = query("SELECT id, favouritedAt FROM songs ORDER BY id").use { cursor ->
    buildMap { while (cursor.moveToNext()) put(cursor.getLong(0), if (cursor.isNull(1)) null else cursor.getLong(1)) }
}

private fun SupportSQLiteDatabase.longs(sql: String): List<Long> = query(sql).use { cursor ->
    buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
}
