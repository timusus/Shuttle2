package com.simplecityapps.localmediaprovider.local.data.room.dao

import com.simplecityapps.localmediaprovider.local.data.room.database.InMemoryDatabaseTest
import com.simplecityapps.localmediaprovider.local.data.room.database.inMemoryMediaDatabaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * #497: a sync merges each remote song's favourite from its server inside [SongDataDao.insertUpdateAndDelete]. A pending
 * local toggle wins; otherwise the server does, keeping a favourite's existing time. Local songs are never touched.
 */
class SongDataDaoServerFavouriteMergeTest : InMemoryDatabaseTest() {
    private val database = inMemoryMediaDatabaseBuilder().build()
    private val dao = database.songDataDao()

    private val localTime = Instant.fromEpochSeconds(1_000)
    private val serverTime = Instant.fromEpochSeconds(2_000)

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a server favourite on a song that isn't one takes the server's time`() = runTest {
        merged(local = null, pending = false, server = serverTime) shouldBe serverTime
    }

    @Test
    fun `a server favourite on a song that already is one keeps its own time`() = runTest {
        merged(local = localTime, pending = false, server = serverTime) shouldBe localTime
    }

    @Test
    fun `a song the server doesn't favourite is cleared`() = runTest {
        merged(local = localTime, pending = false, server = null) shouldBe null
    }

    @Test
    fun `a song neither side favourites stays clear`() = runTest {
        merged(local = null, pending = false, server = null) shouldBe null
    }

    @Test
    fun `a pending favourite wins over the server's not-a-favourite`() = runTest {
        merged(local = localTime, pending = true, server = null) shouldBe localTime
    }

    @Test
    fun `a pending unfavourite wins over the server's favourite`() = runTest {
        merged(local = null, pending = true, server = serverTime) shouldBe null
    }

    @Test
    fun `a pending favourite the server agrees with keeps its own time`() = runTest {
        merged(local = localTime, pending = true, server = serverTime) shouldBe localTime
    }

    @Test
    fun `a pending unfavourite the server agrees with stays clear`() = runTest {
        merged(local = null, pending = true, server = null) shouldBe null
    }

    @Test
    fun `an inserted remote song is stored with the server's favourite`() = runTest {
        dao.insertUpdateAndDelete(
            inserts = listOf(songData("jellyfin://item/a", MediaProviderType.Jellyfin, serverTime), songData("jellyfin://item/b", MediaProviderType.Jellyfin, null)),
            updates = emptyList(),
            deletes = emptyList()
        )

        dao.get().associate { it.path to it.favouritedAt } shouldBe mapOf("jellyfin://item/a" to serverTime, "jellyfin://item/b" to null)
    }

    @Test
    fun `a local song's favourite is never touched by a rescan`() = runTest {
        val favourite = insert(songData("/music/a.mp3", MediaProviderType.MediaStore, null))
        val other = insert(songData("/music/b.mp3", MediaProviderType.Shuttle, null))
        dao.favourite(favourite.id, localTime)

        // A rescan's songs carry no favourite (and would never carry one the user didn't set)
        dao.insertUpdateAndDelete(
            inserts = emptyList(),
            updates = listOf(favourite.copyWith(favouritedAt = null), other.copyWith(favouritedAt = serverTime)),
            deletes = emptyList()
        )

        dao.get().associate { it.path to it.favouritedAt } shouldBe mapOf("/music/a.mp3" to localTime, "/music/b.mp3" to null)
    }

    @Test
    fun `the merge only touches the songs in the update`() = runTest {
        val updated = insert(songData("plex://a", MediaProviderType.Plex, null))
        val untouched = insert(songData("plex://b", MediaProviderType.Plex, null))
        dao.favourite(untouched.id, localTime)

        dao.insertUpdateAndDelete(inserts = emptyList(), updates = listOf(updated.copyWith(favouritedAt = serverTime)), deletes = emptyList())

        dao.get().associate { it.path to it.favouritedAt } shouldBe mapOf("plex://a" to serverTime, "plex://b" to localTime)
    }

    /** A Jellyfin song's favourite after a sync whose server reports [server], from [local], with a pending row if [pending]. */
    private suspend fun merged(
        local: Instant?,
        pending: Boolean,
        server: Instant?
    ): Instant? {
        val stored = insert(songData("jellyfin://item/a", MediaProviderType.Jellyfin, null))
        if (pending) {
            // A toggle made in the app, not yet sent: it writes the local state and its outbox row together
            if (local != null) dao.setFavourite(listOf(stored.toSong().copy(favouritedAt = local)), true) else dao.setFavourite(listOf(stored.toSong()), false)
        } else if (local != null) {
            dao.favourite(stored.id, local)
        }

        dao.insertUpdateAndDelete(inserts = emptyList(), updates = listOf(stored.copyWith(favouritedAt = server)), deletes = emptyList())

        return dao.get().single().favouritedAt
    }

    private suspend fun insert(songData: SongData): SongData {
        dao.insert(listOf(songData))
        return dao.get().first { it.path == songData.path }
    }

    private fun SongData.copyWith(favouritedAt: Instant?): SongData = songData(path, mediaProvider, favouritedAt).also { it.id = id }

    private fun songData(
        path: String,
        mediaProvider: MediaProviderType,
        favouritedAt: Instant?
    ) = SongData(
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
        externalId = path,
        mediaProvider = mediaProvider,
        favouritedAt = favouritedAt
    )
}
