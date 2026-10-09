package com.simplecityapps.localmediaprovider.local.favourites

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** #497 slice 2: the sender drains the outbox through the writer, serially and in order, acking only what the server took. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FavouriteSenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: MediaDatabase
    private lateinit var dao: SongDataDao
    private lateinit var scope: TestScope

    /** Room's queries run on the test scheduler too, so [runCurrent] settles the sender, writer and database together. */
    private fun TestScope.open() {
        scope = this
        database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        dao = database.songDataDao()
    }

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    private class FakeWriter : FavouriteWriter {
        val calls = mutableListOf<String>()
        var succeeds: (Song) -> Boolean = { true }
        var throws = false
        var handled: (Song) -> Boolean = { true }

        override fun handles(song: Song) = handled(song)

        override suspend fun setFavourite(
            song: Song,
            favourite: Boolean
        ): Boolean {
            calls += "${song.externalId} $favourite"
            if (throws) error("offline")
            return succeeds(song)
        }
    }

    private val writer = FakeWriter()

    private fun sender() = FavouriteSender(
        dao = dao,
        writer = writer,
        scope = scope.backgroundScope
    ).also { it.start() }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun `a pending favourite is sent and acked`() = runTest {
        open()
        val song = insertSong("a")
        dao.setFavourite(listOf(song), true)

        sender()
        runCurrent()

        dao.getPendingFavourites().isEmpty() shouldBe true
        writer.calls shouldBe listOf("a true")
    }

    @Test
    fun `rows already queued at start are sent oldest change first`() = runTest {
        open()
        val first = insertSong("a")
        val second = insertSong("b")
        val third = insertSong("c")
        dao.setFavourite(listOf(first), true, at(1))
        dao.setFavourite(listOf(second), true, at(2))
        dao.setFavourite(listOf(third), false, at(3))

        sender()
        runCurrent()

        dao.getPendingFavourites().isEmpty() shouldBe true
        writer.calls shouldBe listOf("a true", "b true", "c false")
    }

    @Test
    fun `a failed send keeps its row and doesn't hold up the rows behind it`() = runTest {
        open()
        val failing = insertSong("a")
        val working = insertSong("b")
        writer.succeeds = { it.externalId != "a" }
        dao.setFavourite(listOf(failing), true, at(1))
        dao.setFavourite(listOf(working), true, at(2))

        sender()
        runCurrent()

        dao.getPendingFavourites().map { it.songId } shouldBe listOf(failing.id)
        writer.calls.first() shouldBe "a true"
        writer.calls.contains("b true") shouldBe true
    }

    @Test
    fun `a writer that throws keeps the row, and the next toggle retries it`() = runTest {
        open()
        val song = insertSong("a")
        writer.throws = true
        dao.setFavourite(listOf(song), true, at(1))
        sender()
        runCurrent()
        writer.calls.size shouldBe 1

        dao.getPendingFavourites().size shouldBe 1

        writer.throws = false
        dao.setFavourite(listOf(song), false, at(2))
        runCurrent()

        dao.getPendingFavourites().isEmpty() shouldBe true
        writer.calls.last() shouldBe "a false"
    }

    @Test
    fun `a song no writer handles is dropped from the outbox`() = runTest {
        open()
        val song = insertSong("a")
        writer.handled = { false }
        dao.setFavourite(listOf(song), true)

        sender()
        runCurrent()

        dao.getPendingFavourites().isEmpty() shouldBe true
        writer.calls shouldBe emptyList()
    }

    @Test
    fun `a song on the exclude list still has its favourite sent`() = runTest {
        open()
        val song = insertSong("a")
        dao.setFavourite(listOf(song), true)
        dao.setExcluded(listOf(song.id), true)

        sender()
        runCurrent()

        dao.getPendingFavourites().isEmpty() shouldBe true
        writer.calls shouldBe listOf("a true")
    }

    @Test
    fun `a row that fails is attempted once per trigger, not again by the acks of the rows after it`() = runTest {
        open()
        val failing = insertSong("a")
        val working = insertSong("b")
        writer.succeeds = { it.externalId != "a" }
        dao.setFavourite(listOf(failing), true, at(1))
        dao.setFavourite(listOf(working), true, at(2))

        sender()
        runCurrent()

        dao.getPendingFavourites().map { it.songId } shouldBe listOf(failing.id)
        // Drains run one at a time, oldest first: once a later toggle is acked, a drain that retried "a" would have shown
        val later = insertSong("c")
        dao.setFavourite(listOf(later), true, at(3))
        runCurrent()
        writer.calls.contains("c true") shouldBe true
        dao.getPendingFavourites().map { it.songId } shouldBe listOf(failing.id)
        writer.calls shouldBe listOf("a true", "b true", "c true")
    }

    private suspend fun insertSong(externalId: String): Song {
        dao.insert(
            listOf(
                SongData(
                    name = "Song $externalId",
                    track = 1,
                    disc = 1,
                    duration = 180_000,
                    year = null,
                    genres = emptyList(),
                    path = "/music/$externalId.mp3",
                    albumArtist = "Artist",
                    artists = listOf("Artist"),
                    album = "Album",
                    size = 0,
                    mimeType = "audio/mpeg",
                    lastModified = Instant.fromEpochMilliseconds(0),
                    lyrics = null,
                    grouping = null,
                    bitRate = null,
                    bitDepth = null,
                    sampleRate = null,
                    channelCount = null,
                    externalId = externalId,
                    mediaProvider = MediaProviderType.Jellyfin
                )
            )
        )
        return dao.get().first { it.externalId == externalId }.toSong()
    }
}
