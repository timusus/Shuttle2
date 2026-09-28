package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPlayHistoryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val songDao = database.songDataDao()
    private val eventDao = database.playEventDao()

    // A Wednesday, 08:00 UTC
    private var now = Instant.parse("2026-09-23T08:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val repository = LocalPlayHistoryRepository(eventDao, freshAlbumIndex(database), clock) { TimeZone.UTC }

    private val albumContext = PlayContext.Album(AlbumGroupKey("blue", AlbumArtistGroupKey("joni mitchell")))
    private val playlistContext = PlayContext.Playlist(7)
    private val genreContext = PlayContext.Genre("Jazz")

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a play is recorded with its local hour, ISO weekday and context`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")

        repository.recordPlay(song, Instant.parse("2026-09-27T21:15:00Z"), 45_000, completed = false, albumContext)

        eventDao.recentContexts(10).single().contextType shouldBe PlayContext.TYPE_ALBUM
        val event = database.query("SELECT localHour, weekday, listenedMs, completed, songPath, mediaProvider FROM play_events", null).use { cursor ->
            cursor.moveToFirst()
            listOf(cursor.getInt(0), cursor.getInt(1), cursor.getLong(2).toInt(), cursor.getInt(3), cursor.getString(4), cursor.getString(5))
        }
        // 2026-09-27 is a Sunday
        event shouldBe listOf(21, 7, 45_000, 0, song.path, "Shuttle")
    }

    @Test
    fun `recent contexts are distinct, most recent first, without none, and limited`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")
        repository.recordPlay(song, now - 3.hours, 200_000, true, albumContext)
        repository.recordPlay(song, now - 2.hours, 200_000, true, playlistContext)
        repository.recordPlay(song, now - 1.hours, 200_000, true, albumContext)
        repository.recordPlay(song, now - 30.minutes, 200_000, true, PlayContext.None)
        repository.recordPlay(song, now - 10.minutes, 200_000, true, genreContext)

        repository.recentContexts(10).map { it.context } shouldBe listOf(genreContext, albumContext, playlistContext)
        repository.recentContexts(10)[1].lastPlayedAt shouldBe now - 1.hours
        repository.recentContexts(2).map { it.context } shouldBe listOf(genreContext, albumContext)
    }

    @Test
    fun `contexts around an hour are ranked by distinct days, with weekend days counted`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")
        // The album at 08:xx on three days, twice on one of them; one of the days a Saturday (2026-09-19).
        repository.recordPlay(song, Instant.parse("2026-09-18T08:10:00Z"), 60_000, true, albumContext)
        repository.recordPlay(song, Instant.parse("2026-09-18T08:40:00Z"), 60_000, true, albumContext)
        repository.recordPlay(song, Instant.parse("2026-09-19T07:50:00Z"), 60_000, true, albumContext)
        repository.recordPlay(song, Instant.parse("2026-09-21T09:20:00Z"), 60_000, true, albumContext)
        // The playlist around then on one day, and in the evening (outside the window) on others.
        repository.recordPlay(song, Instant.parse("2026-09-20T08:05:00Z"), 60_000, true, playlistContext)
        repository.recordPlay(song, Instant.parse("2026-09-21T20:00:00Z"), 60_000, true, playlistContext)
        repository.recordPlay(song, Instant.parse("2026-09-22T20:00:00Z"), 60_000, true, playlistContext)
        // The genre in the window, but before the time asked about.
        repository.recordPlay(song, Instant.parse("2026-06-01T08:00:00Z"), 60_000, true, genreContext)

        val around = repository.contextsAroundHour(hour = 8, windowMinutes = 90, since = now - 60.days, limit = 10)

        around.map { Triple(it.context, it.days, it.weekendDays) } shouldBe listOf(
            Triple(albumContext, 3, 1),
            Triple(playlistContext, 1, 1)
        )
        repository.contextsAroundHour(hour = 8, windowMinutes = 90, since = now - 60.days, limit = 1).map { it.context } shouldBe listOf(albumContext)
    }

    @Test
    fun `the hours around an hour wrap round midnight`() {
        LocalPlayHistoryRepository.hoursAround(8, 90) shouldBe listOf(6, 7, 8, 9)
        LocalPlayHistoryRepository.hoursAround(0, 60) shouldBe listOf(0, 23)
        LocalPlayHistoryRepository.hoursAround(23, 90) shouldBe listOf(0, 21, 22, 23)
        LocalPlayHistoryRepository.hoursAround(8, 0) shouldBe listOf(8)
    }

    @Test
    fun `album and artist completions count plays through since a time`() = runTest {
        val blue1 = insertSong("Blue", "Joni Mitchell", track = 1)
        val blue2 = insertSong("Blue", "Joni Mitchell", track = 2)
        val hejira = insertSong("Hejira", "Joni Mitchell")
        val kid = insertSong("Kid A", "Radiohead")
        repository.recordPlay(blue1, now - 1.days, 200_000, true, albumContext)
        repository.recordPlay(blue2, now - 1.days, 200_000, true, albumContext)
        repository.recordPlay(blue1, now - 2.days, 200_000, true, albumContext)
        repository.recordPlay(hejira, now - 3.days, 200_000, true, PlayContext.None)
        repository.recordPlay(kid, now - 4.days, 200_000, true, PlayContext.None)
        repository.recordPlay(kid, now - 4.days, 40_000, false, PlayContext.None)
        repository.recordPlay(kid, now - 40.days, 200_000, true, PlayContext.None)

        val albums = repository.albumCompletions(since = now - 28.days, halfLife = 14.days, limit = 10)
        albums.map { it.groupKey.key to it.completions } shouldBe listOf("blue" to 3, "hejira" to 1, "kid a" to 1)
        albums.first().groupKey.albumArtistGroupKey shouldBe AlbumArtistGroupKey("joni mitchell")
        albums.first().lastCompletedAt shouldBe now - 1.days
        repository.albumCompletions(since = now - 28.days, halfLife = 14.days, limit = 1).map { it.groupKey.key } shouldBe listOf("blue")

        repository.albumArtistCompletions(since = now - 28.days, halfLife = 14.days, limit = 10).map { it.groupKey.key to it.completions } shouldBe
            listOf("joni mitchell" to 4, "radiohead" to 1)
    }

    @Test
    fun `completions are scored by age, halving every half-life`() = runTest {
        val fresh = insertSong("Blue", "Joni Mitchell")
        val old = insertSong("Kid A", "Radiohead")
        repository.recordPlay(fresh, now, 200_000, true, PlayContext.None)
        repository.recordPlay(old, now - 14.days, 200_000, true, PlayContext.None)
        repository.recordPlay(old, now - 14.days, 200_000, true, PlayContext.None)
        repository.recordPlay(old, now - 14.days, 200_000, true, PlayContext.None)

        val albums = repository.albumCompletions(since = now - 28.days, halfLife = 14.days, limit = 10)

        albums.map { it.groupKey.key to it.score } shouldBe listOf("kid a" to 1.5, "blue" to 1.0)
        albums.map { it.completions } shouldBe listOf(3, 1)
    }

    @Test
    fun `completions group albums and artists by the keys the album and artist repositories use`() = runTest {
        // Tagged three ways SQL can't tell are one album: case, a leading article, punctuation
        songDao.insert(
            listOf(
                createSongData(album = "OK Computer", albumArtist = "The Radiohead", track = 1),
                createSongData(album = "ok computer", albumArtist = "Radiohead", track = 2),
                createSongData(album = "OK Computer.", albumArtist = "radiohead", track = 3)
            )
        )
        songDao.get().forEach { repository.recordPlay(it.toSong(), now - 1.days, 200_000, true, PlayContext.None) }

        val albums = repository.albumCompletions(since = now - 28.days, halfLife = 14.days, limit = 10)
        val artists = repository.albumArtistCompletions(since = now - 28.days, halfLife = 14.days, limit = 10)

        val repositoryAlbums = LocalAlbumRepository(backgroundScope, songDao).getAlbums(AlbumQuery.All()).first()
        val repositoryArtists = LocalAlbumArtistRepository(backgroundScope, songDao).getAlbumArtists(AlbumArtistQuery.All()).first()
        albums.map { it.groupKey to it.completions } shouldBe listOf(repositoryAlbums.single().groupKey to 3)
        artists.map { it.groupKey to it.completions } shouldBe listOf(repositoryArtists.single().groupKey to 3)
    }

    @Test
    fun `genre plays count every play of each of a song's genres, scored by age`() = runTest {
        songDao.insert(
            listOf(
                createSongData(album = "Blue", track = 1).copy(genres = listOf("Folk", "Pop")),
                createSongData(album = "Kind of Blue", track = 1).copy(genres = listOf("Jazz"))
            )
        )
        val (folk, jazz) = songDao.get().sortedBy { it.album }.map { it.toSong() }
        repository.recordPlay(folk, now, 40_000, false, PlayContext.None)
        repository.recordPlay(jazz, now - 14.days, 200_000, true, PlayContext.None)
        repository.recordPlay(jazz, now - 100.days, 200_000, true, PlayContext.None)

        repository.genrePlays(since = now - 90.days, halfLife = 14.days, limit = 10).map { Triple(it.genre, it.plays, it.score) } shouldBe
            listOf(Triple("Folk", 1, 1.0), Triple("Pop", 1, 1.0), Triple("Jazz", 1, 0.5))
    }

    @Test
    fun `the event count follows the history`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")
        repository.eventCount().first() shouldBe 0

        repository.recordPlay(song, now, 200_000, true, albumContext)

        repository.eventCount().first() shouldBe 1
    }

    @Test
    fun `a play survives its song's reimport under a new row id`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")
        repository.recordPlay(song, now - 1.days, 200_000, true, albumContext)
        songDao.delete(songDao.get())
        insertSong("Blue", "Joni Mitchell")

        repository.albumCompletions(since = now - 28.days, halfLife = 14.days, limit = 10).single().completions shouldBe 1
    }

    @Test
    fun `clearing the history forgets every play`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")
        repository.recordPlay(song, now, 200_000, true, albumContext)

        repository.clearHistory()

        repository.recentContexts(10).shouldBeEmpty()
        eventDao.count() shouldBe 0
    }

    @Test
    fun `recording prunes plays older than the retention period and beyond the cap, at most daily`() = runTest {
        insertEvent(now - 400.days)
        insertEvent(now - 10.days)
        val song = insertSong("Blue", "Joni Mitchell")

        repository.recordPlay(song, now, 200_000, true, albumContext)
        eventDao.count() shouldBe 2

        // Not again the same day
        insertEvent(now - 400.days)
        repository.recordPlay(song, now, 200_000, true, albumContext)
        eventDao.count() shouldBe 4

        now += 1.days
        repository.recordPlay(song, now, 200_000, true, albumContext)
        eventDao.count() shouldBe 4
    }

    @Test
    fun `the prune keeps only the latest events past the cap`() = runTest {
        (1..5).forEach { insertEvent(now - it.days) }

        eventDao.prune(before = now - PlayHistoryRepository.RETENTION_DAYS.days, keep = 3)

        eventDao.recentContexts(10).single().lastPlayedAt shouldBe now - 1.days
        eventDao.count() shouldBe 3
    }

    private suspend fun insertEvent(at: Instant, listened: Duration = 3.minutes) {
        eventDao.insert(
            PlayEventData(MediaProviderType.Shuttle, "/old.mp3", at, listened.inWholeMilliseconds, true, 8, 3, PlayContext.TYPE_GENRE, "Old")
        )
    }

    @Test
    fun `a play in an album context kept from before the identity rule is recorded under the album's key now`() = runTest {
        songDao.insert(
            listOf(
                createSongData(album = "Drive OST", track = 1).copy(albumArtist = null, artists = listOf("Kavinsky"), path = "/music/Drive/1.mp3"),
                createSongData(album = "Drive OST", track = 2).copy(albumArtist = null, artists = listOf("College"), path = "/music/Drive/2.mp3")
            )
        )
        val song = songDao.get().first { it.track == 1 }.toSong()
        // A queue saved before #637 names the album by its track artist
        val legacy = PlayContext.decode(PlayContext.TYPE_ALBUM, "=kavinsky\u001F=drive ost")

        repository.recordPlay(song, now, 200_000, true, legacy)

        repository.recentContexts(10).map { it.context } shouldBe
            listOf(PlayContext.Album(AlbumGroupKey("drive ost", AlbumArtistGroupKey("various artists"), "dir:/music/Drive")))
    }

    private suspend fun insertSong(
        album: String,
        albumArtist: String,
        track: Int = 1
    ): Song {
        songDao.insert(listOf(createSongData(album = album, albumArtist = albumArtist, track = track)))
        return songDao.get().first { it.album == album && it.track == track }.toSong()
    }
}
