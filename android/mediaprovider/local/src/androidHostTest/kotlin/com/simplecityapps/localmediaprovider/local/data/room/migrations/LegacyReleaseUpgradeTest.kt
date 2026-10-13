package com.simplecityapps.localmediaprovider.local.data.room.migrations

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "legacy-release-upgrade-test"

/** song.db as Play's 1.0.10 (commit 2c586573c) left it: schema 40, the last version the legacy app shipped. */
private const val LEGACY_RELEASE_VERSION = 40

private const val SAF_SONG = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FBjork%2FJoga.mp3"
private const val LAST_PLAYED = 1_700_000_000_000L
private const val LAST_COMPLETED = 1_700_000_100_000L
private const val LAST_MODIFIED = 1_600_000_000_000L

/**
 * A library, playlists, play counts and favourites written by the legacy release survive the upgrade to the current
 * schema, and the current DAOs read them back through the app's own [DatabaseProvider].
 */
@RunWith(AndroidJUnit4::class)
class LegacyReleaseUpgradeTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    private fun createLegacyDatabase() {
        helper.createDatabase(TEST_DB, LEGACY_RELEASE_VERSION).apply {
            insertSong(id = 1, path = SAF_SONG, provider = "Shuttle", playCount = 12, lastPlayed = LAST_PLAYED, lastCompleted = LAST_COMPLETED, position = 42_000)
            insertSong(id = 2, path = "/storage/emulated/0/Music/Low.mp3", provider = "MediaStore", playCount = 3, lastCompleted = LAST_COMPLETED + 1)
            insertSong(id = 3, path = "jellyfin://item/abc", provider = "Jellyfin", externalId = "abc")
            insertSong(id = 4, path = "/storage/emulated/0/Music/Hidden.mp3", provider = "MediaStore", blacklisted = true)
            insertSong(id = 5, path = "/storage/emulated/0/Music/Blue.mp3", provider = "MediaStore", playCount = 7)
            insertPlaylist(id = 1, name = "Road trip", sortOrder = "SongName")
            insertPlaylist(id = 2, name = "Favorites")
            insertPlaylist(id = 3, name = "Mix", externalId = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FMix.m3u")
            insertEntries(playlistId = 1, songIds = listOf(2, 1, 3))
            insertEntries(playlistId = 2, songIds = listOf(5, 3))
            insertEntries(playlistId = 3, songIds = listOf(1))
            close()
        }
    }

    @Test
    fun `the legacy release's rows survive every migration to the current schema`() {
        createLegacyDatabase()

        val chain = ALL_MIGRATIONS.filter { it.startVersion >= LEGACY_RELEASE_VERSION }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, ALL_MIGRATIONS.last().endVersion, true, *chain.toTypedArray())

        migrated.rows("SELECT id, path, mediaProvider, playCount, lastPlayed, lastCompleted, playbackPosition, blacklisted, replayGainTrack, dateAdded FROM songs ORDER BY id") shouldContainExactly
            listOf(
                listOf(1L, SAF_SONG, "Shuttle", 12L, LAST_PLAYED, LAST_COMPLETED, 42_000L, 0L, -6.5, LAST_MODIFIED),
                listOf(2L, "/storage/emulated/0/Music/Low.mp3", "MediaStore", 3L, null, LAST_COMPLETED + 1, 0L, 0L, -6.5, LAST_MODIFIED),
                listOf(3L, "jellyfin://item/abc", "Jellyfin", 0L, null, null, 0L, 0L, -6.5, LAST_MODIFIED),
                listOf(4L, "/storage/emulated/0/Music/Hidden.mp3", "MediaStore", 0L, null, null, 0L, 1L, -6.5, LAST_MODIFIED),
                listOf(5L, "/storage/emulated/0/Music/Blue.mp3", "MediaStore", 7L, null, null, 0L, 0L, -6.5, LAST_MODIFIED)
            )
        // The Favorites playlist became the favourite flag; the user's own playlists keep their ids and entry order
        migrated.rows("SELECT id, name, sortOrder, sortDescending, externalId FROM playlists ORDER BY id") shouldContainExactly
            listOf(
                listOf(1L, "Road trip", "SongName", 0L, null),
                listOf(3L, "Mix", "Position", 0L, "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FMix.m3u")
            )
        migrated.rows("SELECT playlistId, songId FROM playlist_song_join ORDER BY playlistId, sortOrder") shouldContainExactly
            listOf(listOf(1L, 2L), listOf(1L, 1L), listOf(1L, 3L), listOf(3L, 1L))
        val favouritedAt = migrated.rows("SELECT id, favouritedAt FROM songs ORDER BY id").associate { (id, at) -> id as Long to at as Long? }
        listOf(1L, 2L, 4L).forEach { favouritedAt.getValue(it) shouldBe null }
        // The playlist's later entry is the more recent favourite
        favouritedAt.getValue(3)!! shouldBeGreaterThan favouritedAt.getValue(5)!!
        // A server song's favourite is queued for its server, which would otherwise win the next sync and clear it
        migrated.rows("SELECT songId, favourite FROM pending_favourites") shouldContainExactly listOf(listOf(3L, 1L))
        // Each song's last play through seeds the listening history
        migrated.rows("SELECT songPath, startedAt, completed FROM play_events ORDER BY startedAt") shouldContainExactly
            listOf(listOf(SAF_SONG, LAST_COMPLETED, 1L), listOf("/storage/emulated/0/Music/Low.mp3", LAST_COMPLETED + 1, 1L))
    }

    @Test
    fun `the app opens the legacy release's database and reads its library, playlists and favourites`() = runTest {
        createLegacyDatabase()

        val database = DatabaseProvider(Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MediaDatabase::class.java, TEST_DB)).database
        try {
            val songs = database.songDataDao().getAll().first().associateBy { it.id }
            songs.keys shouldBe setOf(1L, 2L, 3L, 4L, 5L)
            songs.getValue(4).blacklisted shouldBe true
            songs.getValue(1).playCount shouldBe 12
            songs.getValue(1).playbackPosition shouldBe 42_000
            songs.getValue(1).lastPlayed shouldBe Instant.fromEpochMilliseconds(LAST_PLAYED)
            songs.getValue(1).mediaProvider shouldBe MediaProviderType.Shuttle
            songs.getValue(5).favouritedAt shouldNotBe null
            songs.getValue(1).favouritedAt shouldBe null

            val playlists = database.playlistDataDao().getAll().first().associateBy { it.id }
            playlists.keys shouldBe setOf(1L, 3L)
            playlists.getValue(1).sortOrder shouldBe PlaylistSongSortOrder.SongName
            playlists.getValue(1).songCount shouldBe 3
            database.playlistSongJoinDataDao().getSongsForPlaylist(1).first().sortedBy { it.sortOrder }.map { it.song.id } shouldContainExactly listOf(2L, 1L, 3L)
        } finally {
            database.close()
        }
    }
}

private fun SupportSQLiteDatabase.insertSong(
    id: Long,
    path: String,
    provider: String,
    externalId: String? = null,
    playCount: Int = 0,
    lastPlayed: Long? = null,
    lastCompleted: Long? = null,
    position: Int = 0,
    blacklisted: Boolean = false
) {
    execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, " +
            "playbackPosition, playCount, lastPlayed, lastCompleted, blacklisted, externalId, mediaProvider, replayGainTrack, replayGainAlbum, " +
            "lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount) " +
            "VALUES (?, 'Song $id', 1, 1, 180000, '1997-01-01', 'Rock;Pop', ?, 'Artist', 'Artist;Guest', 'Album', 5000, 'audio/mpeg', ?, " +
            "?, ?, ?, ?, ?, ?, ?, -6.5, -7.0, NULL, NULL, 320, 16, 44100, 2)",
        arrayOf<Any?>(id, path, LAST_MODIFIED, position, playCount, lastPlayed, lastCompleted, if (blacklisted) 1 else 0, externalId, provider)
    )
}

private fun SupportSQLiteDatabase.insertPlaylist(
    id: Long,
    name: String,
    sortOrder: String = "Position",
    externalId: String? = null
) {
    execSQL(
        "INSERT INTO playlists (id, name, sortOrder, mediaProvider, externalId) VALUES (?, ?, ?, 'Shuttle', ?)",
        arrayOf<Any?>(id, name, sortOrder, externalId)
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

private fun SupportSQLiteDatabase.rows(sql: String): List<List<Any?>> = query(sql).use { cursor ->
    buildList {
        while (cursor.moveToNext()) {
            add(
                (0 until cursor.columnCount).map { column ->
                    when (cursor.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                        else -> cursor.getString(column)
                    }
                }
            )
        }
    }
}
