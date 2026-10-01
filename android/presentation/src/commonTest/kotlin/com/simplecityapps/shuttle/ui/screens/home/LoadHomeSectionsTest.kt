package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSuggestionsRepository
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumDay
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.GenrePlays
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.shuttle.model.playContext
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

class LoadHomeSectionsTest {
    private val suggestions = FakeSuggestionsRepository()
    private val playHistory = FakePlayHistoryRepository()
    private val resolve = ResolveHomeItems(suggestions, FakePlaylistRepository())

    private val now = Instant.parse("2026-09-23T08:30:00Z")
    private val homeTime = HomeTime(
        object : Clock {
            override fun now() = this@LoadHomeSectionsTest.now
        },
    ) { TimeZone.UTC }

    private val albums = (1..30).map { createAlbum("album $it", "artist ${it % 3}") }
    private val artists = (0..2).map { createAlbumArtist("artist $it") }
    private val genres = (1..8).map { createGenre("Genre $it", songCount = 20 + it) }

    private val jumpBackIn = JumpBackIn(playHistory, suggestions, resolve)
    private val aroundThisTime = AroundThisTime(playHistory, resolve)
    private val heavyRotation = HeavyRotation(playHistory, resolve)
    private val rediscover = Rediscover(suggestions, resolve)
    private val recentlyAdded = RecentlyAdded(suggestions, resolve)
    private val genrePicks = GenrePicks(playHistory, suggestions)
    private val load = LoadHomeSections(jumpBackIn, aroundThisTime, heavyRotation, rediscover, recentlyAdded, genrePicks, playHistory, homeTime)

    init {
        suggestions.albums = albums
        suggestions.albumArtists = artists
        suggestions.genres = genres
        suggestions.recentlyCompleted = albums.take(4).map { it.groupKey!! }
        suggestions.recentlyAdded = albums.drop(20).map { it.groupKey!! }
        suggestions.toRediscover = albums.drop(12).take(8).map { it.groupKey!! }
        playHistory.recentContexts = albums.take(5).map { RecentContext(it.playContext, now) }
        playHistory.contextsAroundHour = albums.drop(5).take(4).map { ContextDays(it.playContext, days = 4, weekendDays = 1, lastPlayedAt = now) }
        playHistory.albumDays = albums.drop(9).take(3).flatMap { album -> (0L..3L).map { AlbumDay(album.groupKey!!, 20_000L - it, 4, 10, now - it.days) } }
        playHistory.genrePlays = genres.take(3).map { GenrePlays(it.name, plays = 4, score = 2.0) }
        suggestions.latency = 10.milliseconds
    }

    private suspend fun <T> TestScope.timed(block: suspend () -> T): Pair<T, Long> {
        val started = currentTime
        return block() to currentTime - started
    }

    @Test
    fun `every section loads side by side, and they come out as one after another would give them`() = runTest {
        val (jumpBackIn, jumpBackInTime) = timed { jumpBackIn() }
        val (aroundThisTime, aroundThisTimeTime) = timed { aroundThisTime(now, TimeZone.UTC) }
        val (heavyRotation, heavyRotationTime) = timed { heavyRotation(now) }
        val (rediscover, rediscoverTime) = timed { rediscover(now) }
        val (recentlyAdded, recentlyAddedTime) = timed { recentlyAdded() }
        val (genrePicks, genrePicksTime) = timed { genrePicks(now) }
        val oneAfterAnother = assembleHomeSections(
            HomeCandidates(true, jumpBackIn, aroundThisTime, heavyRotation, rediscover, recentlyAdded, genrePicks),
            homeTime.clock,
            TimeZone.UTC,
        )

        val (sections, loadTime) = timed { load(hasHistory = true) }

        sections shouldBe oneAfterAnother
        sections.map { it.id } shouldBe listOf(
            HomeSectionId.JumpBackIn,
            HomeSectionId.AroundThisTime,
            HomeSectionId.HeavyRotation,
            HomeSectionId.Rediscover,
            HomeSectionId.RecentlyAdded,
            HomeSectionId.GenrePicks,
        )
        loadTime shouldBe listOf(jumpBackInTime, aroundThisTimeTime, heavyRotationTime, rediscoverTime, recentlyAddedTime, genrePicksTime).max()
    }
}
