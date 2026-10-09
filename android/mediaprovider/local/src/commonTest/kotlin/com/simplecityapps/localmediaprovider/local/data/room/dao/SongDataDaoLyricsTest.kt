package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.useReaderConnection
import com.simplecityapps.localmediaprovider.local.data.room.database.InMemoryDatabaseTest
import com.simplecityapps.localmediaprovider.local.data.room.database.inMemoryMediaDatabaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_COLUMNS
import com.simplecityapps.localmediaprovider.local.data.room.entity.SONG_COLUMNS_QUALIFIED
import com.simplecityapps.localmediaprovider.local.data.room.entity.toSongDataUpdate
import com.simplecityapps.localmediaprovider.local.repository.createSongData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The song lists leave lyrics out (#873); [SongDataDao.lyrics] reads one song's, and an update writes them. */
class SongDataDaoLyricsTest : InMemoryDatabaseTest() {
    private val database = inMemoryMediaDatabaseBuilder().build()
    private val dao = database.songDataDao()

    @AfterTest
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
    fun `the song columns are every column but lyrics`() = runTest {
        val columns = database.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA table_info(songs)") { statement ->
                val nameColumn = statement.getColumnNames().indexOf("name")
                val names = mutableListOf<String>()
                while (statement.step()) names += statement.getText(nameColumn)
                names
            }
        }

        SONG_COLUMNS.split(", ") shouldContainExactlyInAnyOrder columns - "lyrics"
        SONG_COLUMNS_QUALIFIED.split(", ") shouldContainExactlyInAnyOrder (columns - "lyrics").map { "songs.$it" }
    }
}
