package com.simplecityapps.localmediaprovider.local.favourites

import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.InMemoryDatabaseTest
import com.simplecityapps.localmediaprovider.local.data.room.database.inMemoryMediaDatabaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** #497: the sender's retry backoff and its survival of a failing drain, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavouriteSenderRetryTest : InMemoryDatabaseTest() {
    private class FakeWriter : FavouriteWriter {
        val calls = mutableListOf<String>()
        var succeeds = false

        override fun handles(song: Song) = true

        override suspend fun setFavourite(
            song: Song,
            favourite: Boolean
        ): Boolean {
            calls += "${song.externalId} $favourite"
            return succeeds
        }
    }

    @Test
    fun `a failed row is retried after the backoff which then doubles and the row is acked once it succeeds`() = runTest {
        val database = inMemoryMediaDatabaseBuilder()
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        val dao = database.songDataDao()
        val writer = FakeWriter()
        dao.insert(listOf(songData("a")))
        dao.setFavourite(listOf(dao.get().first().toSong()), true)

        FavouriteSender(dao, writer, backgroundScope).start()
        runCurrent()
        writer.calls shouldBe listOf("a true")

        advanceTimeBy(FavouriteSender.INITIAL_BACKOFF_MS - 1)
        runCurrent()
        writer.calls.size shouldBe 1
        advanceTimeBy(2)
        runCurrent()
        writer.calls.size shouldBe 2

        // Failed again, so the next wait is twice as long.
        advanceTimeBy(2 * FavouriteSender.INITIAL_BACKOFF_MS - 3)
        runCurrent()
        writer.calls.size shouldBe 2
        writer.succeeds = true
        advanceTimeBy(4)
        runCurrent()
        writer.calls.size shouldBe 3
        dao.getPendingFavourites().isEmpty() shouldBe true

        advanceTimeBy(10 * FavouriteSender.MAX_BACKOFF_MS)
        runCurrent()
        writer.calls.size shouldBe 3
        database.close()
    }

    @Test
    fun `an exception outside the send does not stop the sender`() = runTest {
        val database = inMemoryMediaDatabaseBuilder()
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        val dao = database.songDataDao()
        val writer = object : FavouriteWriter {
            val calls = mutableListOf<String>()
            var explodeInHandles = true

            override fun handles(song: Song): Boolean {
                if (explodeInHandles) error("boom")
                return true
            }

            override suspend fun setFavourite(
                song: Song,
                favourite: Boolean
            ): Boolean {
                calls += "${song.externalId} $favourite"
                return true
            }
        }
        dao.insert(listOf(songData("a"), songData("b")))
        val (a, b) = dao.get().sortedBy { it.externalId }.map { it.toSong() }
        dao.setFavourite(listOf(a), true)

        FavouriteSender(dao, writer, backgroundScope).start()
        runCurrent()
        writer.calls shouldBe emptyList()

        writer.explodeInHandles = false
        dao.setFavourite(listOf(b), true)
        runCurrent()

        writer.calls.toSet() shouldBe setOf("a true", "b true")
        database.close()
    }

    @Test
    fun `a drain that throws is retried after the backoff without waiting for a new toggle`() = runTest {
        val database = inMemoryMediaDatabaseBuilder()
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        val dao = database.songDataDao()
        val writer = object : FavouriteWriter {
            val calls = mutableListOf<String>()
            var explodeInHandles = true

            override fun handles(song: Song): Boolean {
                if (explodeInHandles) error("boom")
                return true
            }

            override suspend fun setFavourite(
                song: Song,
                favourite: Boolean
            ): Boolean {
                calls += "${song.externalId} $favourite"
                return true
            }
        }
        dao.insert(listOf(songData("a")))
        val a = dao.get().single().toSong()
        dao.setFavourite(listOf(a), true)

        FavouriteSender(dao, writer, backgroundScope).start()
        runCurrent()
        writer.calls shouldBe emptyList()

        writer.explodeInHandles = false
        advanceTimeBy(FavouriteSender.INITIAL_BACKOFF_MS + 1)
        runCurrent()

        writer.calls shouldBe listOf("a true")
        dao.getPendingFavourites() shouldBe emptyList()
        database.close()
    }

    private fun songData(externalId: String) = SongData(
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
}
