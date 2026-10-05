package com.simplecityapps.localmediaprovider.local.data.room.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #772: a sync folds each remote song's play stats from its server into the row inside [SongDataDao.insertUpdateAndDelete],
 * through [SongDataDao.applyServerPlayStats]: the larger play count and the later last-played time, never a sum.
 */
@RunWith(AndroidJUnit4::class)
class SongDataDaoServerPlayStatsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.songDataDao()

    private val earlier = Instant.fromEpochSeconds(1_000)
    private val later = Instant.fromEpochSeconds(2_000)

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a higher server play count and a later last played replace the stored ones`() = runTest {
        applied(storedCount = 2, storedLastPlayed = earlier, serverCount = 5, serverLastPlayed = later) shouldBe (5 to later)
    }

    @Test
    fun `a lower server play count and an earlier last played leave the stored ones`() = runTest {
        applied(storedCount = 5, storedLastPlayed = later, serverCount = 2, serverLastPlayed = earlier) shouldBe (5 to later)
    }

    @Test
    fun `the count and the time are each taken from whichever side is larger`() = runTest {
        applied(storedCount = 5, storedLastPlayed = earlier, serverCount = 2, serverLastPlayed = later) shouldBe (5 to later)
    }

    @Test
    fun `equal play counts aren't added together`() = runTest {
        applied(storedCount = 3, storedLastPlayed = later, serverCount = 3, serverLastPlayed = later) shouldBe (3 to later)
    }

    @Test
    fun `a server last played fills a song never played here`() = runTest {
        applied(storedCount = 0, storedLastPlayed = null, serverCount = 1, serverLastPlayed = later) shouldBe (1 to later)
    }

    @Test
    fun `a server that sends no last played leaves the stored one`() = runTest {
        applied(storedCount = 1, storedLastPlayed = earlier, serverCount = 4, serverLastPlayed = null) shouldBe (4 to earlier)
    }

    @Test
    fun `a song with no plays on either side stays unplayed`() = runTest {
        applied(storedCount = 0, storedLastPlayed = null, serverCount = 0, serverLastPlayed = null) shouldBe (0 to null)
    }

    @Test
    fun `only the song named is changed`() = runTest {
        val target = insert(songData("jellyfin://item/a", MediaProviderType.Jellyfin))
        val other = insert(songData("jellyfin://item/b", MediaProviderType.Jellyfin))

        dao.applyServerPlayStats(target.id, 7, later)

        dao.get().associate { it.path to (it.playCount to it.lastPlayed) } shouldBe mapOf("jellyfin://item/a" to (7 to later), "jellyfin://item/b" to (other.playCount to null))
    }

    @Test
    fun `a sync's song with plays on the server merges them - a local song's plays are never touched`() = runTest {
        val remote = insert(songData("jellyfin://item/a", MediaProviderType.Jellyfin, playCount = 1, lastPlayed = earlier))
        val local = insert(songData("/music/a.mp3", MediaProviderType.MediaStore, playCount = 4, lastPlayed = later))

        dao.insertUpdateAndDelete(
            inserts = emptyList(),
            updates = listOf(songData(remote.path, remote.mediaProvider, playCount = 3, lastPlayed = later).also { it.id = remote.id }, songData(local.path, local.mediaProvider, playCount = 9, lastPlayed = later).also { it.id = local.id }),
            deletes = emptyList()
        )

        dao.get().associate { it.path to (it.playCount to it.lastPlayed) } shouldBe mapOf("jellyfin://item/a" to (3 to later), "/music/a.mp3" to (4 to later))
    }

    /** A stored song's play stats after the server reports [serverCount] and [serverLastPlayed]. */
    private suspend fun applied(
        storedCount: Int,
        storedLastPlayed: Instant?,
        serverCount: Int,
        serverLastPlayed: Instant?
    ): Pair<Int, Instant?> {
        val stored = insert(songData("jellyfin://item/a", MediaProviderType.Jellyfin, storedCount, storedLastPlayed))

        dao.applyServerPlayStats(stored.id, serverCount, serverLastPlayed)

        return dao.get().single().let { it.playCount to it.lastPlayed }
    }

    private suspend fun insert(songData: SongData): SongData {
        dao.insert(listOf(songData))
        return dao.get().first { it.path == songData.path }
    }

    private fun songData(
        path: String,
        mediaProvider: MediaProviderType,
        playCount: Int = 0,
        lastPlayed: Instant? = null
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
        playCount = playCount,
        lastPlayed = lastPlayed
    )
}
