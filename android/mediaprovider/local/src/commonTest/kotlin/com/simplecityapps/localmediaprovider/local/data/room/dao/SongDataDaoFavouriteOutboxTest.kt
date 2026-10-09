package com.simplecityapps.localmediaprovider.local.data.room.dao

import com.simplecityapps.localmediaprovider.local.data.room.database.InMemoryDatabaseTest
import com.simplecityapps.localmediaprovider.local.data.room.database.inMemoryMediaDatabaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** #497 slice 1: a remote-provider song's favourite/unfavourite enqueues a `pending_favourites` row, in the same transaction, and acking deletes it. */
class SongDataDaoFavouriteOutboxTest : InMemoryDatabaseTest() {
    private val database = inMemoryMediaDatabaseBuilder().build()
    private val dao = database.songDataDao()

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `favouriting a remote song enqueues a pending favourite`() = runTest {
        val song = insertSong(MediaProviderType.Jellyfin, externalId = "item-1")

        dao.setFavourite(listOf(song), true)

        val pending = dao.getPendingFavourites()
        pending.map { it.songId } shouldBe listOf(song.id)
        pending.single().favourite shouldBe true
    }

    @Test
    fun `unfavouriting a remote song enqueues a pending unfavourite`() = runTest {
        val song = insertSong(MediaProviderType.Plex, externalId = "item-2")
        dao.setFavourite(listOf(song), true)

        dao.setFavourite(listOf(song), false)

        val pending = dao.getPendingFavourites()
        pending.map { it.songId } shouldBe listOf(song.id)
        pending.single().favourite shouldBe false
    }

    @Test
    fun `undoing a remove enqueues a pending favourite again`() = runTest {
        val song = insertSong(MediaProviderType.Emby, externalId = "item-3")
        dao.setFavourite(listOf(song), true)
        val originalFavouritedSong = dao.get().single().toSong()
        dao.setFavourite(listOf(originalFavouritedSong), false)

        dao.setFavourite(listOf(originalFavouritedSong), true)

        dao.getPendingFavourites().single().favourite shouldBe true
    }

    @Test
    fun `a later toggle before a flush overwrites the earlier pending row instead of adding a second one`() = runTest {
        val song = insertSong(MediaProviderType.Jellyfin, externalId = "item-4")

        dao.setFavourite(listOf(song), true)
        dao.setFavourite(listOf(song), false)

        val pending = dao.getPendingFavourites()
        pending.size shouldBe 1
        pending.single().favourite shouldBe false
    }

    @Test
    fun `a local song never enqueues a pending favourite`() = runTest {
        val song = insertSong(MediaProviderType.Shuttle, externalId = null)

        dao.setFavourite(listOf(song), true)
        dao.setFavourite(listOf(song), false)

        dao.getPendingFavourites().shouldBeEmpty()
    }

    @Test
    fun `acking a pending favourite deletes its row`() = runTest {
        val song = insertSong(MediaProviderType.Jellyfin, externalId = "item-5")
        dao.setFavourite(listOf(song), true)

        dao.ackPendingFavourite(dao.getPendingFavourites().single()) shouldBe true

        dao.getPendingFavourites().shouldBeEmpty()
    }

    @Test
    fun `acking a row that was replaced while it was being sent keeps the newer row`() = runTest {
        val song = insertSong(MediaProviderType.Jellyfin, externalId = "item-6")
        dao.setFavourite(listOf(song), true)
        val sent = dao.getPendingFavourites().single()

        dao.setFavourite(listOf(song), false)

        dao.ackPendingFavourite(sent) shouldBe false
        dao.getPendingFavourites().single().favourite shouldBe false
    }

    @Test
    fun `observing the outbox emits on enqueue and on ack with the oldest change first`() = runTest {
        val first = insertSong(MediaProviderType.Jellyfin, externalId = "a", path = "/music/a.mp3")
        val second = insertSong(MediaProviderType.Jellyfin, externalId = "b", path = "/music/b.mp3")

        dao.observePendingFavourites().first().shouldBeEmpty()
        dao.setFavourite(listOf(first), true)
        dao.setFavourite(listOf(second), true)
        dao.observePendingFavourites().first().map { it.songId } shouldBe listOf(first.id, second.id)

        dao.ackPendingFavourite(dao.getPendingFavourites().first())
        dao.observePendingFavourites().first().map { it.songId } shouldBe listOf(second.id)
    }

    private suspend fun insertSong(
        mediaProvider: MediaProviderType,
        externalId: String?,
        path: String = "/music/${mediaProvider.name}.mp3"
    ): com.simplecityapps.shuttle.model.Song {
        dao.insert(
            listOf(
                SongData(
                    name = "Song",
                    track = 1,
                    disc = 1,
                    duration = 180_000,
                    year = null,
                    genres = emptyList(),
                    path = path,
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
                    mediaProvider = mediaProvider
                )
            )
        )
        return dao.get().first { it.path == path }.toSong()
    }
}
