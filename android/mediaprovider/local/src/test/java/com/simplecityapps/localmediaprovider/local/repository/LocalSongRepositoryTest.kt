package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
        .setQueryCallback(RoomDatabase.QueryCallback { sql, _ -> if (sql.contains("FROM songs")) songQueries += sql }, Executor(Runnable::run))
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
