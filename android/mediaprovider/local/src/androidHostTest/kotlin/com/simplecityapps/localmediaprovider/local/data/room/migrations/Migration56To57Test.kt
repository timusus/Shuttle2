package com.simplecityapps.localmediaprovider.local.data.room.migrations

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.provider.taglib.FakeMediaStore
import com.simplecityapps.localmediaprovider.local.provider.taglib.MediaStoreAudioFile
import com.simplecityapps.localmediaprovider.local.provider.taglib.MediaStoreAudioLister
import com.simplecityapps.localmediaprovider.local.provider.taglib.MediaStoreAudioRow
import com.simplecityapps.localmediaprovider.local.provider.taglib.MovedFile
import com.simplecityapps.localmediaprovider.local.provider.taglib.RoomMediaStoreListingStore
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-56-57-test"

@RunWith(AndroidJUnit4::class)
class Migration56To57Test {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MediaDatabase::class.java
        )

    @Test
    fun `migrate 56 to 57 copies the listing to each local provider with songs`() {
        helper.createDatabase(TEST_DB, 56).apply {
            insertSong(1, "/music/a.flac", "Shuttle")
            insertSong(2, "/music/b.flac", "MediaStore")
            insertSong(3, "https://jellyfin/b", "Jellyfin")
            insertListing()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57)

        migrated.rows("SELECT provider, id, generation, path, duration FROM media_store_files") shouldContainExactlyInAnyOrder
            listOf("Shuttle|7|-1|/music/a.flac|185000", "MediaStore|7|-1|/music/a.flac|185000")
        migrated.rows("SELECT provider, version FROM media_store_scan_state") shouldContainExactlyInAnyOrder listOf("Shuttle|v1", "MediaStore|v1")
        migrated.rows("SELECT path FROM songs WHERE id = 1") shouldBe listOf("/music/a.flac")
    }

    @Test
    fun `migrate 56 to 57 copies the listing to the one local provider with songs`() {
        helper.createDatabase(TEST_DB, 56).apply {
            insertSong(1, "/music/a.flac", "MediaStore")
            insertListing()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57)

        migrated.rows("SELECT provider, id, generation FROM media_store_files") shouldBe listOf("MediaStore|7|-1")
        migrated.rows("SELECT provider, version FROM media_store_scan_state") shouldBe listOf("MediaStore|v1")
    }

    @Test
    fun `migrate 56 to 57 keeps no listing without local songs`() {
        helper.createDatabase(TEST_DB, 56).apply {
            insertListing()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57)

        migrated.rows("SELECT provider FROM media_store_files") shouldBe emptyList()
        migrated.rows("SELECT provider FROM media_store_scan_state") shouldBe emptyList()
    }

    @Test
    fun `the first import after migrating reads every row again, as the copied listing is another provider's`() = runTest {
        helper.createDatabase(TEST_DB, 56).apply {
            insertSong(1, "/music/a.flac", "Shuttle")
            insertSong(2, "/music/a.flac", "MediaStore")
            insertListing()
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57).close()
        // The generation the copied listing has, with another size: a copied row is read again rather than trusted
        val source = FakeMediaStore().apply { put(row(path = "/music/a.flac", size = 20, generation = 3)) }

        withLister(source) { lister ->
            lister.list(whole = false) shouldBe listOf(file(path = "/music/a.flac", size = 20))
        }
        source.changedAfter shouldBe listOf(-1L)
    }

    @Test
    fun `the first import after migrating finds a file moved since the copied listing`() = runTest {
        helper.createDatabase(TEST_DB, 56).apply {
            insertSong(1, "/music/a.flac", "Shuttle")
            insertListing()
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 57, true, MIGRATION_56_57).close()
        val source = FakeMediaStore().apply { put(row(path = "/music/moved.flac", size = 10, generation = 4)) }

        withLister(source) { lister ->
            lister.list(whole = false)
            lister.moved() shouldBe listOf(MovedFile(oldPath = "/music/a.flac", file = file(path = "/music/moved.flac", size = 10)))
        }
    }

    private suspend fun withLister(
        source: FakeMediaStore,
        block: suspend (MediaStoreAudioLister) -> Unit
    ) {
        val database =
            Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, MediaDatabase::class.java, TEST_DB)
                .allowMainThreadQueries()
                .build()
        try {
            block(MediaStoreAudioLister(source, RoomMediaStoreListingStore(database.mediaStoreFileDao(), MediaProviderType.Shuttle), incremental = true))
        } finally {
            database.close()
        }
    }

    private fun file(
        path: String,
        size: Long
    ) = MediaStoreAudioFile(id = 7, path = path, displayName = path.substringAfterLast('/'), size = size, lastModified = 1000, mimeType = "audio/flac", duration = 185000)

    private fun row(
        path: String,
        size: Long,
        generation: Long
    ) = MediaStoreAudioRow(file(path, size), generation)

    private fun SupportSQLiteDatabase.insertListing() {
        execSQL("INSERT INTO media_store_files (id, generation, path, displayName, size, lastModified, mimeType, duration) VALUES (7, 3, '/music/a.flac', 'a.flac', 10, 1000, 'audio/flac', 185000)")
        execSQL("INSERT INTO media_store_scan_state (id, version) VALUES (0, 'v1')")
    }

    private fun SupportSQLiteDatabase.insertSong(
        id: Long,
        path: String,
        provider: String
    ) = execSQL(
        "INSERT INTO songs (id, name, track, disc, duration, year, genres, path, albumArtist, artists, album, size, mimeType, lastModified, playbackPosition, playCount, blacklisted, mediaProvider, lyrics, grouping, bitRate, bitDepth, sampleRate, channelCount, lastCompleted, dateAdded) " +
            "VALUES ($id, 'Song', 1, 1, 180000, NULL, '', '$path', 'Artist', 'Artist', 'Album', 0, 'audio/flac', 0, 0, 0, 0, '$provider', NULL, NULL, NULL, NULL, NULL, NULL, 1000, 2000)"
    )

    private fun SupportSQLiteDatabase.rows(sql: String): List<String> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).joinToString("|") { column -> cursor.getString(column) })
        }
    }
}
