package com.simplecityapps.localmediaprovider.local.favourites

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** #497 slice 2: the sender drains the outbox through the writer, serially and in order, acking only what the server took. */
@RunWith(AndroidJUnit4::class)
class FavouriteSenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.songDataDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        scope = scope
    ).also { it.start() }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun `a pending favourite is sent and acked`(): Unit = runBlocking {
        val song = insertSong("a")
        dao.setFavourite(listOf(song), true)

        sender()

        eventually { dao.getPendingFavourites().isEmpty() }
        writer.calls shouldBe listOf("a true")
    }

    @Test
    fun `rows already queued at start are sent oldest change first`(): Unit = runBlocking {
        val first = insertSong("a")
        val second = insertSong("b")
        val third = insertSong("c")
        dao.setFavourite(listOf(first), true)
        delay(5)
        dao.setFavourite(listOf(second), true)
        delay(5)
        dao.setFavourite(listOf(third), false)

        sender()

        eventually { dao.getPendingFavourites().isEmpty() }
        writer.calls shouldBe listOf("a true", "b true", "c false")
    }

    @Test
    fun `a failed send keeps its row and doesn't hold up the rows behind it`(): Unit = runBlocking {
        val failing = insertSong("a")
        val working = insertSong("b")
        writer.succeeds = { it.externalId != "a" }
        dao.setFavourite(listOf(failing), true)
        delay(5)
        dao.setFavourite(listOf(working), true)

        sender()

        eventually { dao.getPendingFavourites().map { it.songId } == listOf(failing.id) }
        writer.calls.first() shouldBe "a true"
        writer.calls.contains("b true") shouldBe true
    }

    @Test
    fun `a writer that throws keeps the row, and the next toggle retries it`(): Unit = runBlocking {
        val song = insertSong("a")
        writer.throws = true
        dao.setFavourite(listOf(song), true)
        sender()
        eventually { writer.calls.size == 1 }

        dao.getPendingFavourites().size shouldBe 1

        writer.throws = false
        dao.setFavourite(listOf(song), false)

        eventually { dao.getPendingFavourites().isEmpty() }
        writer.calls.last() shouldBe "a false"
    }

    @Test
    fun `a song no writer handles is dropped from the outbox`(): Unit = runBlocking {
        val song = insertSong("a")
        writer.handled = { false }
        dao.setFavourite(listOf(song), true)

        sender()

        eventually { dao.getPendingFavourites().isEmpty() }
        writer.calls shouldBe emptyList()
    }

    @Test
    fun `a song on the exclude list still has its favourite sent`(): Unit = runBlocking {
        val song = insertSong("a")
        dao.setFavourite(listOf(song), true)
        dao.setExcluded(listOf(song.id), true)

        sender()

        eventually { dao.getPendingFavourites().isEmpty() }
        writer.calls shouldBe listOf("a true")
    }

    @Test
    fun `a row that fails is attempted once per trigger, not again by the acks of the rows after it`(): Unit = runBlocking {
        val failing = insertSong("a")
        val working = insertSong("b")
        writer.succeeds = { it.externalId != "a" }
        dao.setFavourite(listOf(failing), true)
        delay(5)
        dao.setFavourite(listOf(working), true)

        sender()

        eventually { dao.getPendingFavourites().map { it.songId } == listOf(failing.id) }
        // Drains run one at a time, oldest first: once a later toggle is acked, a drain that retried "a" would have shown
        val later = insertSong("c")
        dao.setFavourite(listOf(later), true)
        eventually { writer.calls.contains("c true") && dao.getPendingFavourites().map { it.songId } == listOf(failing.id) }
        writer.calls shouldBe listOf("a true", "b true", "c true")
    }

    private suspend fun eventually(condition: suspend () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
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
