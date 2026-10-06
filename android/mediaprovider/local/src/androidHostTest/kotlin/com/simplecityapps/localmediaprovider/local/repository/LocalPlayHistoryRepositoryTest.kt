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
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
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
    private val repository = LocalPlayHistoryRepository(eventDao, database.resumePointDao(), freshAlbumIndex(database), clock) { TimeZone.UTC }

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
    fun `a play written at its threshold counts as played through once completed`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")
        val id = repository.recordPlay(song, now - 1.hours, 100_000, completed = false, albumContext)!!
        repository.albumDays(since = now - 28.days) shouldBe emptyList()

        repository.completePlay(id, 200_000)

        repository.albumDays(since = now - 28.days).single().songs shouldBe 1
        eventDao.count() shouldBe 1
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
    fun `album days count the distinct songs played through each local day, with the album's track count`() = runTest {
        val blue1 = insertSong("Blue", "Joni Mitchell", track = 1)
        val blue2 = insertSong("Blue", "Joni Mitchell", track = 2)
        insertSong("Blue", "Joni Mitchell", track = 3)
        val kid = insertSong("Kid A", "Radiohead")
        repository.recordPlay(blue1, now - 1.days, 200_000, true, albumContext)
        repository.recordPlay(blue1, now - 1.days + 1.hours, 200_000, true, albumContext)
        repository.recordPlay(blue2, now - 1.days, 200_000, true, albumContext)
        repository.recordPlay(blue1, now - 2.days, 200_000, true, albumContext)
        repository.recordPlay(kid, now - 4.days, 40_000, false, PlayContext.None)
        repository.recordPlay(kid, now - 40.days, 200_000, true, PlayContext.None)

        val days = repository.albumDays(since = now - 28.days).sortedByDescending { it.day }
        val today = now.toEpochMilliseconds() / 86_400_000

        days.map { Triple(it.groupKey.key, today - it.day, it.songs) } shouldBe listOf(Triple("blue", 1L, 2), Triple("blue", 2L, 1))
        days.map { it.trackCount } shouldBe listOf(3, 3)
        days.first().lastCompletedAt shouldBe now - 1.days + 1.hours
        days.first().albumArtistKeys shouldBe listOf(AlbumArtistGroupKey("joni mitchell"))
    }

    @Test
    fun `an album day names each of the album's album artists and not its featured one`() = runTest {
        songDao.insert(
            listOf(
                createSongData(album = "Watch the Throne", track = 1).copy(albumArtist = null, albumArtists = listOf("Jay-Z", "Kanye West")),
                createSongData(album = "Duets", albumArtist = "Radiohead feat. Björk", track = 1)
            )
        )
        songDao.get().forEach { repository.recordPlay(it.toSong(), now - 1.days, 200_000, true, PlayContext.None) }

        repository.albumDays(since = now - 28.days).associate { it.groupKey.key to it.albumArtistKeys } shouldBe mapOf(
            "watch the throne" to listOf(AlbumArtistGroupKey("jay-z"), AlbumArtistGroupKey("kanye west")),
            "duets" to listOf(AlbumArtistGroupKey("radiohead"))
        )
    }

    @Test
    fun `album days split at local midnight`() = runTest {
        // 23:30 and 00:30 in Melbourne (UTC+10) are two days there, one in UTC
        val melbourne = LocalPlayHistoryRepository(eventDao, database.resumePointDao(), freshAlbumIndex(database), clock) { TimeZone.of("Australia/Melbourne") }
        val song = insertSong("Blue", "Joni Mitchell")
        melbourne.recordPlay(song, Instant.parse("2026-09-21T13:30:00Z"), 200_000, true, albumContext)
        melbourne.recordPlay(song, Instant.parse("2026-09-21T14:30:00Z"), 200_000, true, albumContext)

        melbourne.albumDays(since = now - 28.days).size shouldBe 2
        repository.albumDays(since = now - 28.days).size shouldBe 1
    }

    @Test
    fun `album days count a day with a single play through, however much else was played`() = runTest {
        val blue = insertSong("Blue", "Joni Mitchell")
        val kid = insertSong("Kid A", "Radiohead")
        repeat(50) { repository.recordPlay(blue, now - 1.days + it.minutes, 200_000, true, albumContext) }
        (1..10).forEach { repository.recordPlay(kid, now - it.days, 200_000, true, PlayContext.None) }

        val days = repository.albumDays(since = now - 28.days).groupBy { it.groupKey.key }

        days.mapValues { (_, albumDays) -> albumDays.size } shouldBe mapOf("blue" to 1, "kid a" to 10)
    }

    @Test
    fun `genre plays are aged by local day`() = runTest {
        // 01:00 today in Melbourne (UTC+10) is still yesterday in UTC
        val melbourne = LocalPlayHistoryRepository(eventDao, database.resumePointDao(), freshAlbumIndex(database), clock) { TimeZone.of("Australia/Melbourne") }
        songDao.insert(listOf(createSongData(album = "Kind of Blue").copy(genres = listOf("Jazz"))))
        melbourne.recordPlay(songDao.get().single().toSong(), Instant.parse("2026-09-22T15:00:00Z"), 200_000, true, PlayContext.None)

        melbourne.genrePlays(since = now - 90.days, halfLife = 14.days, limit = 10).single().score shouldBe 1.0
    }

    @Test
    fun `album days group albums by the keys the album repository uses`() = runTest {
        // Tagged three ways SQL can't tell are one album: case, a leading article, punctuation
        songDao.insert(
            listOf(
                createSongData(album = "OK Computer", albumArtist = "The Radiohead", track = 1),
                createSongData(album = "ok computer", albumArtist = "Radiohead", track = 2),
                createSongData(album = "OK Computer.", albumArtist = "radiohead", track = 3)
            )
        )
        songDao.get().forEach { repository.recordPlay(it.toSong(), now - 1.days, 200_000, true, PlayContext.None) }

        val days = repository.albumDays(since = now - 28.days)

        val repositoryAlbums = LocalAlbumRepository(backgroundScope, songDao).getAlbums(AlbumQuery.All()).first()
        val repositoryArtists = LocalAlbumArtistRepository(backgroundScope, songDao).getAlbumArtists(AlbumArtistQuery.All()).first()
        days.map { Triple(it.groupKey, it.songs, it.trackCount) } shouldBe listOf(Triple(repositoryAlbums.single().groupKey, 3, 3))
        days.single().albumArtistKeys shouldBe listOf(repositoryArtists.single().groupKey)
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

        repository.albumDays(since = now - 28.days).single().songs shouldBe 1
    }

    @Test
    fun `clearing the history forgets every play and where each context was left`() = runTest {
        val song = insertSong("Blue", "Joni Mitchell")
        repository.recordPlay(song, now, 200_000, true, albumContext)
        repository.saveResumePoint(resumePoint(albumContext))

        repository.clearHistory()

        repository.recentContexts(10).shouldBeEmpty()
        eventDao.count() shouldBe 0
        repository.resumePoint(albumContext).shouldBeNull()
    }

    @Test
    fun `a context's resume point is read back, and a later one replaces it`() = runTest {
        repository.saveResumePoint(resumePoint(albumContext, track = 2))
        repository.saveResumePoint(resumePoint(playlistContext, track = 1))

        repository.saveResumePoint(resumePoint(albumContext, track = 4, finished = true))

        repository.resumePoint(albumContext) shouldBe resumePoint(albumContext, track = 4, finished = true)
        repository.resumePoint(playlistContext) shouldBe resumePoint(playlistContext, track = 1)
        repository.resumePoint(genreContext).shouldBeNull()
    }

    @Test
    fun `a resume point is read back with its song's name and length while the song is in the library`() = runTest {
        songDao.insert(listOf(createSongData(album = "Blue").copy(name = "River", path = "/music/2.flac", duration = 240_000)))
        repository.saveResumePoint(resumePoint(albumContext, track = 2))
        repository.saveResumePoint(resumePoint(playlistContext, track = 3))

        repository.resumePoint(albumContext) shouldBe resumePoint(albumContext, track = 2).copy(songName = "River", songDurationMs = 240_000)
        repository.resumePoint(playlistContext) shouldBe resumePoint(playlistContext, track = 3)
    }

    @Test
    fun `resume points are read together, each as it's read alone, and only for the contexts that have one`() = runTest {
        songDao.insert(listOf(createSongData(album = "Blue").copy(name = "River", path = "/music/2.flac", duration = 240_000)))
        repository.saveResumePoint(resumePoint(albumContext, track = 2))
        repository.saveResumePoint(resumePoint(playlistContext, track = 3))
        val contexts = listOf(albumContext, playlistContext, genreContext, PlayContext.None, albumContext)

        val points = repository.resumePointsFor(contexts)

        points shouldBe mapOf(
            albumContext to resumePoint(albumContext, track = 2).copy(songName = "River", songDurationMs = 240_000),
            playlistContext to resumePoint(playlistContext, track = 3)
        )
        contexts.distinct().forEach { context -> points[context] shouldBe repository.resumePoint(context) }
        repository.resumePointsFor(emptyList()) shouldBe emptyMap()
    }

    @Test
    fun `a queue from no context has no resume point`() = runTest {
        repository.saveResumePoint(resumePoint(PlayContext.None))

        repository.resumePoint(PlayContext.None).shouldBeNull()
    }

    private fun resumePoint(
        context: PlayContext,
        track: Int = 0,
        finished: Boolean = false
    ) = ResumePoint(context, MediaProviderType.Shuttle, "/music/$track.flac", 61_000, track, 12, shuffled = true, finished, now)

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
