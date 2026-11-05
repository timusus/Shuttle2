package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSuggestionsRepository
import com.simplecityapps.fakes.fakeObserveSmartPlaylistSongs
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumDay
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.home.HeavyRotation
import com.simplecityapps.shuttle.ui.screens.home.HomeRankedAlbumKeys
import com.simplecityapps.shuttle.ui.screens.home.HomeTime
import com.simplecityapps.shuttle.ui.screens.home.Rediscover
import com.simplecityapps.shuttle.ui.screens.home.ResolveHomeItems
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

class ObserveSmartPlaylistSongsTest {
    private val now = Instant.parse("2026-09-22T22:00:00Z")
    private val today = 20_000L

    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val suggestions = FakeSuggestionsRepository()
    private val playHistory = FakePlayHistoryRepository()

    private val blueSongs = listOf(
        createSong(id = 2, name = "b2", album = "blue", albumArtist = "joni mitchell", track = 2),
        createSong(id = 1, name = "b1", album = "blue", albumArtist = "joni mitchell", track = 1),
    )
    private val courtSongs = listOf(createSong(id = 3, name = "c1", album = "court and spark", albumArtist = "joni mitchell", track = 1))
    private val kidASongs = listOf(createSong(id = 4, name = "k1", album = "kid a", albumArtist = "radiohead", track = 1))
    private val otherSongs = listOf(createSong(id = 5, name = "o1", album = "other", albumArtist = "someone", track = 1))

    private val blue = album("blue", "joni mitchell", blueSongs)
    private val court = album("court and spark", "joni mitchell", courtSongs)
    private val kidA = album("kid a", "radiohead", kidASongs)

    private fun album(
        name: String,
        artist: String,
        songs: List<Song>,
    ) = createAlbum(name, artist, groupKey = songs.first().albumGroupKey)

    private val observe = fakeObserveSmartPlaylistSongs(
        songRepository,
        rankedAlbumKeys = HomeRankedAlbumKeys(
            Rediscover(suggestions, ResolveHomeItems(suggestions, FakePlaylistRepository())),
            HeavyRotation(playHistory, ResolveHomeItems(suggestions, FakePlaylistRepository())),
            HomeTime(
                object : Clock {
                    override fun now() = now
                },
                { TimeZone.UTC },
            ),
        ),
    )

    init {
        songRepository.setSongs(blueSongs + courtSongs + kidASongs + otherSongs)
        suggestions.albums = listOf(blue, court, kidA)
        suggestions.albumArtists = listOf(createAlbumArtist("joni mitchell"), createAlbumArtist("radiohead"))
    }

    private fun day(
        album: AlbumGroupKey?,
        day: Long,
        songs: Int,
    ) = AlbumDay(album!!, day, songs, trackCount = 10, lastCompletedAt = now - (today - day).days)

    private suspend fun songIds(id: SmartPlaylistId) = observe(id).first().map { it.id }

    @Test
    fun `rediscover lists the albums Home ranks best first with songs in track order`() = runTest {
        suggestions.toRediscover = listOf(kidA.groupKey!!, blue.groupKey!!)

        songIds(SmartPlaylistId.Rediscover) shouldBe listOf(4L, 1L, 2L)
    }

    @Test
    fun `heavy rotation puts an artist's played albums at the artist's rank and lists no album twice`() = runTest {
        playHistory.albumDays = listOf(
            day(blue.groupKey, today, 3),
            day(blue.groupKey, today - 3, 3),
            day(blue.groupKey, today - 6, 3),
            day(court.groupKey, today - 3, 1),
            day(kidA.groupKey, today - 5, 4),
            day(kidA.groupKey, today - 8, 4),
            day(kidA.groupKey, today - 10, 4),
        )

        // Blue ranks first as an album, then Joni Mitchell adds Blue again (skipped) and Court and Spark, then Kid A
        songIds(SmartPlaylistId.HeavyRotation) shouldBe listOf(1L, 2L, 3L, 4L)
    }

    @Test
    fun `a ranked playlist with nothing ranked is empty`() = runTest {
        songIds(SmartPlaylistId.Rediscover) shouldBe emptyList()
        songIds(SmartPlaylistId.HeavyRotation) shouldBe emptyList()
    }
}
