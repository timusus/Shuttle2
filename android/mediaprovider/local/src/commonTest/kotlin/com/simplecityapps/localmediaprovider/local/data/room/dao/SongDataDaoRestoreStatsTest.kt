package com.simplecityapps.localmediaprovider.local.data.room.dao

import com.simplecityapps.localmediaprovider.local.data.room.database.InMemoryDatabaseTest
import com.simplecityapps.localmediaprovider.local.data.room.database.inMemoryMediaDatabaseBuilder
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.mediaprovider.repository.songs.SongStatsRestore
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Library-backup restore merges into the row as it is when the transaction runs, never overwriting newer device data. */
class SongDataDaoRestoreStatsTest : InMemoryDatabaseTest() {
    private val database = inMemoryMediaDatabaseBuilder().build()
    private val dao = database.songDataDao()

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    private fun restore(
        song: Song,
        playCount: Int = song.playCount,
        lastPlayed: Instant? = song.lastPlayed,
        lastCompleted: Instant? = song.lastCompleted,
        playbackPosition: Int = song.playbackPosition,
        dateAdded: Instant? = song.dateAdded,
        favouritedAt: Instant? = null
    ) = SongStatsRestore(song, playCount, lastPlayed, lastCompleted, playbackPosition, dateAdded, excluded = song.blacklisted, favouritedAt = favouritedAt)

    @Test
    fun `newer device values survive a restore of older ones`() = runTest {
        val song = insert(playCount = 10, lastPlayed = at(5_000), lastCompleted = at(4_000), playbackPosition = 900, dateAdded = at(1_000))

        // A snapshot taken before the device moved on is what the restore was merged against.
        dao.restoreStats(listOf(restore(song, playCount = 3, lastPlayed = at(2_000), lastCompleted = at(1_500), playbackPosition = 100, dateAdded = at(2_000))))

        dao.get().single().run {
            playCount shouldBe 10
            lastPlayed shouldBe at(5_000)
            lastCompleted shouldBe at(4_000)
            playbackPosition shouldBe 900
            dateAdded shouldBe at(1_000)
        }
    }

    @Test
    fun `newer backup values and its position are taken together with the earlier date added`() = runTest {
        val song = insert(playCount = 1, lastPlayed = at(2_000), lastCompleted = at(1_000), playbackPosition = 100, dateAdded = at(3_000))

        dao.restoreStats(listOf(restore(song, playCount = 8, lastPlayed = at(9_000), lastCompleted = at(8_000), playbackPosition = 700, dateAdded = at(500))))

        dao.get().single().run {
            playCount shouldBe 8
            lastPlayed shouldBe at(9_000)
            lastCompleted shouldBe at(8_000)
            playbackPosition shouldBe 700
            dateAdded shouldBe at(500)
        }
    }

    @Test
    fun `a missing value on either side keeps the other`() = runTest {
        val song = insert(lastPlayed = null, lastCompleted = at(1_000), dateAdded = null)

        dao.restoreStats(listOf(restore(song, lastPlayed = at(2_000), lastCompleted = null, dateAdded = at(300), playbackPosition = 50)))

        dao.get().single().run {
            lastPlayed shouldBe at(2_000)
            lastCompleted shouldBe at(1_000)
            dateAdded shouldBe at(300)
            playbackPosition shouldBe 50
        }
    }

    @Test
    fun `only a remote song's restored favourite is queued for the server`() = runTest {
        val remote = insert(path = "/remote.mp3", provider = MediaProviderType.Jellyfin, externalId = "item-1")
        val local = insert(path = "/local.mp3", provider = MediaProviderType.Shuttle, externalId = null)

        dao.restoreStats(listOf(restore(remote, favouritedAt = at(1_000)), restore(local, favouritedAt = at(1_000))))

        dao.getPendingFavourites().map { it.songId } shouldBe listOf(remote.id)
        dao.get().all { it.favouritedAt == at(1_000) } shouldBe true
    }

    private suspend fun insert(
        path: String = "/music/song.mp3",
        provider: MediaProviderType = MediaProviderType.Shuttle,
        externalId: String? = null,
        playCount: Int = 0,
        lastPlayed: Instant? = null,
        lastCompleted: Instant? = null,
        playbackPosition: Int = 0,
        dateAdded: Instant? = null
    ): Song {
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
                    mediaProvider = provider,
                    playCount = playCount,
                    lastPlayed = lastPlayed,
                    lastCompleted = lastCompleted,
                    playbackPosition = playbackPosition,
                    dateAdded = dateAdded
                )
            )
        )
        return dao.get().first { it.path == path }.toSong()
    }
}
