package com.simplecityapps.localmediaprovider.local.data.room.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_COLUMNS
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongDataUpdate
import com.simplecityapps.localmediaprovider.local.repository.createSongData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The song lists leave lyrics out (#873); [SongDataDao.lyrics] reads one song's, and an update writes them. */
@RunWith(AndroidJUnit4::class)
class SongDataDaoLyricsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.songDataDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `lyrics are read by id and left out of the song lists`() = runTest {
        dao.insert(listOf(createSongData(album = "A", track = 1).copy(lyrics = "la la la"), createSongData(album = "A", track = 2)))
        val (withLyrics, without) = dao.get().sortedBy { it.track }.map { it.id }

        dao.lyrics(withLyrics) shouldBe "la la la"
        dao.lyrics(without) shouldBe null
        dao.lyrics(999) shouldBe null
        dao.get().map { it.lyrics }.distinct() shouldBe listOf(null)
        dao.getAllSongData().first().map { it.lyrics }.distinct() shouldBe listOf(null)
        dao.songDataByIds(listOf(withLyrics)).single().lyrics shouldBe null
    }

    @Test
    fun `an update writes the lyrics it carries`() = runTest {
        dao.insert(listOf(createSongData(album = "A")))
        val stored = dao.get().single()

        dao.update(stored.copy(lyrics = "new words").also { it.id = stored.id }.toSongDataUpdate())

        dao.lyrics(stored.id) shouldBe "new words"
    }

    @Test
    fun `the song columns are every column but lyrics`() {
        val columns = database.openHelper.readableDatabase.query("PRAGMA table_info(songs)").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
        }

        SONG_COLUMNS.split(", ") shouldContainExactlyInAnyOrder columns - "lyrics"
    }
}
