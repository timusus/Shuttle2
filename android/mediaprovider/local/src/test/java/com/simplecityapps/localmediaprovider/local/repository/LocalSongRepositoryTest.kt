package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Song reads against a real (in-memory) database, recording the SQL each one runs. */
@RunWith(AndroidJUnit4::class)
class LocalSongRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val songQueries = CopyOnWriteArrayList<String>()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryCallback(RoomDatabase.QueryCallback { sql, _ -> if (sql.contains("FROM songs") || sql.contains("UPDATE songs")) songQueries += sql }, Executor(Runnable::run))
        .build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `songs by id are read by id, not from the whole library`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        val songs = insertSongs((1..5).map { index -> "Song $index" })
        songQueries.clear()

        val restored = repository.getSongs(SongQuery.SongIds(listOf(songs[3].id, songs[1].id, songs[3].id))).first()

        restored.orEmpty().map(Song::name) shouldContainExactlyInAnyOrder listOf("Song 2", "Song 4")
        songQueries.isNotEmpty() shouldBe true
        songQueries.filterNot { sql -> sql.contains("WHERE id IN") } shouldBe emptyList()
    }

    @Test
    fun `more ids than SQLite binds in one statement are all read`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        val songs = insertSongs((1..1_500).map { index -> "Song $index" })

        val restored = repository.getSongs(SongQuery.SongIds(songs.map(Song::id))).first()

        restored.orEmpty().map(Song::id) shouldContainExactlyInAnyOrder songs.map(Song::id)
    }

    @Test
    fun `excluded songs are left out of songs by id`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        val (kept, excluded) = insertSongs(listOf("Kept", "Excluded"))
        repository.setExcluded(listOf(excluded), true)

        repository.getSongs(SongQuery.SongIds(listOf(kept.id, excluded.id))).first().orEmpty().map(Song::name) shouldBe listOf("Kept")
    }

    @Test
    fun `no ids reads nothing`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        insertSongs(listOf("Song"))
        songQueries.clear()

        repository.getSongs(SongQuery.SongIds(emptyList())).first() shouldBe emptyList()
        songQueries shouldBe emptyList()
    }

    @Test
    fun `a library query comes in its sort order`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        insertSongs(listOf("Cherry", "Apple", "Banana"))

        val sorted = repository.getSongs(SongQuery.All(sortOrder = SongSortOrder.SongName)).filterNotNull().first()

        sorted.map(Song::name) shouldBe listOf("Apple", "Banana", "Cherry")
    }

    @Test
    fun `a new song's date added is its modification time, and later updates keep it`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        val modified = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val template = songData("Template").toSong()
        repository.insert(listOf(template.copy(lastModified = modified, dateAdded = null)), MediaProviderType.Shuttle)
        val inserted = repository.loadSongs(SongQuery.All()).single()

        repository.update(inserted.copy(name = "Retagged", lastModified = modified + 1.days))

        repository.loadSongs(SongQuery.All()).single().run {
            name shouldBe "Retagged"
            dateAdded shouldBe modified
        }
    }

    @Test
    fun `a track played through updates its position and play count in one write`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        val song = insertSongs(listOf("Song")).single()
        songQueries.clear()

        repository.recordPlayedThrough(song.copy(duration = 180_000))

        songQueries.count { sql -> sql.contains("UPDATE songs") } shouldBe 1
        val updated = database.songDataDao().get().single().toSong()
        updated.playbackPosition shouldBe 180_000
        updated.playCount shouldBe 1
    }

    @Test
    fun `a metadata write reports the songs it updated, and a play count or position write doesn't`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        val (first, second, third) = insertSongs(listOf("First", "Second", "Third"))
        val updates = mutableListOf<Set<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.updatedSongIds.toList(updates) }

        repository.update(first.copy(name = "Retagged"))
        repository.update(listOf(second.copy(name = "Retagged")))
        repository.insertUpdateAndDelete(inserts = emptyList(), updates = listOf(third.copy(name = "Rescanned")), deletes = emptyList(), mediaProviderType = MediaProviderType.Shuttle)
        repository.insertUpdateAndDelete(inserts = emptyList(), updates = emptyList(), deletes = listOf(first), mediaProviderType = MediaProviderType.Shuttle)
        repository.recordPlayedThrough(second)
        repository.setPlaybackPosition(second, 1_000)

        updates shouldBe listOf(setOf(first.id), setOf(second.id), setOf(third.id))
    }

    @Test
    fun `a modification time in the future counts as added now`() = runTest {
        val repository = LocalSongRepository(backgroundScope, database.songDataDao())
        val template = songData("Template").toSong()
        repository.insert(listOf(template.copy(lastModified = Clock.System.now() + 365.days, dateAdded = null)), MediaProviderType.Shuttle)

        repository.loadSongs(SongQuery.All()).single().dateAdded!! shouldBeLessThanOrEqualTo Clock.System.now()
    }

    private suspend fun insertSongs(names: List<String>): List<Song> {
        database.songDataDao().insert(names.map(::songData))
        return database.songDataDao().get().map { songData -> songData.toSong() }.sortedBy(Song::id)
    }

    private fun songData(name: String) = SongData(
        name = name,
        track = 1,
        disc = 1,
        duration = 180_000,
        year = null,
        genres = emptyList(),
        path = "/music/$name.mp3",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = Date(0),
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
