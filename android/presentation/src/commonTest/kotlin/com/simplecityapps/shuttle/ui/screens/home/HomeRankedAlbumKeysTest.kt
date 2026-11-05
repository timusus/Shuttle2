package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSuggestionsRepository
import com.simplecityapps.mediaprovider.repository.playhistory.AlbumDay
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.SmartPlaylistId
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

class HomeRankedAlbumKeysTest {
    private val now = Instant.parse("2026-09-22T22:00:00Z")
    private val today = 20_000L
    private val suggestions = FakeSuggestionsRepository()
    private val playHistory = FakePlayHistoryRepository()
    private val resolve = ResolveHomeItems(suggestions, FakePlaylistRepository())
    private val blue = createAlbum("blue", "joni mitchell")
    private val kidA = createAlbum("kid a", "radiohead")

    private val keys = HomeRankedAlbumKeys(
        Rediscover(suggestions, resolve),
        HeavyRotation(playHistory, resolve),
        HomeTime(
            object : Clock {
                override fun now(): Instant = this@HomeRankedAlbumKeysTest.now
            },
        ) { TimeZone.UTC },
    )

    init {
        suggestions.albums = listOf(blue, kidA)
        suggestions.albumArtists = listOf(createAlbumArtist("joni mitchell"))
    }

    private fun day(
        album: AlbumGroupKey?,
        day: Long,
    ) = AlbumDay(album!!, day, songs = 3, trackCount = 10, lastCompletedAt = now - (today - day).days)

    @Test
    fun `heavy rotation lists only albums played on the shelf's minimum days`() = runTest {
        playHistory.albumDays = listOf(
            day(blue.groupKey, today),
            day(blue.groupKey, today - 1),
            day(blue.groupKey, today - 2),
            day(kidA.groupKey, today),
        )

        keys(SmartPlaylistId.HeavyRotation) shouldBe listOf(blue.groupKey!!)
    }
}
