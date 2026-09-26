package com.simplecityapps.shuttle.smartplaylist

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test

class SmartRulesSortAndLimitTest {
    private fun at(seconds: Long) = Instant.fromEpochSeconds(seconds)

    private val songs = listOf(
        song(id = 1, name = "Charlie", artists = listOf("B"), albumArtist = "B", album = "Y", date = LocalDate(1990, 1, 1), duration = 3_000, playCount = 2, lastPlayed = at(20), lastCompleted = at(30), dateAdded = at(100), lastModified = at(1_000)),
        song(id = 2, name = "alpha", artists = listOf("C"), albumArtist = "C", album = "Z", date = null, duration = 1_000, playCount = 9, lastPlayed = null, lastCompleted = at(10), dateAdded = at(300), lastModified = at(3_000)),
        song(id = 3, name = "Bravo", artists = listOf("A"), albumArtist = "A", album = "X", date = LocalDate(1980, 1, 1), duration = 2_000, playCount = 0, lastPlayed = at(10), lastCompleted = null, dateAdded = at(200), lastModified = at(2_000)),
    )

    private fun order(
        sort: SmartSort,
        descending: Boolean = false,
    ) = SmartRules(sort = sort, descending = descending).sortAndLimit(songs, seed = 0).map { song -> song.id }

    @Test
    fun `each sort orders by its own key, ascending with the missing first`() {
        SmartSort.entries.filterNot { sort -> sort == SmartSort.Random }.associateWith { sort -> order(sort) } shouldBe mapOf(
            // Album order: album X, Y, Z
            SmartSort.Default to listOf(3L, 1L, 2L),
            SmartSort.Title to listOf(2L, 3L, 1L),
            SmartSort.Artist to listOf(3L, 1L, 2L),
            SmartSort.Album to listOf(3L, 1L, 2L),
            SmartSort.Year to listOf(2L, 3L, 1L),
            SmartSort.Duration to listOf(2L, 3L, 1L),
            SmartSort.PlayCount to listOf(3L, 1L, 2L),
            SmartSort.LastPlayed to listOf(2L, 3L, 1L),
            SmartSort.LastCompleted to listOf(3L, 2L, 1L),
            SmartSort.DateAdded to listOf(1L, 3L, 2L),
            SmartSort.Modified to listOf(1L, 3L, 2L),
        )
    }

    @Test
    fun `descending reverses the order, putting the missing last`() {
        order(SmartSort.PlayCount, descending = true) shouldBe listOf(2L, 1L, 3L)
        order(SmartSort.LastPlayed, descending = true) shouldBe listOf(1L, 3L, 2L)
    }

    @Test
    fun `ties fall back to album order`() {
        val tied = listOf(
            song(id = 1, album = "B", playCount = 1),
            song(id = 2, album = "A", playCount = 1),
            song(id = 3, album = "C", playCount = 1),
        )
        SmartRules(sort = SmartSort.PlayCount, descending = true).sortAndLimit(tied, seed = 0).map { song -> song.id } shouldBe listOf(2L, 1L, 3L)
    }

    @Test
    fun `random order holds for a seed and changes with it`() {
        val many = (1L..50L).map { id -> song(id = id) }
        val rules = SmartRules(sort = SmartSort.Random)

        val first = rules.sortAndLimit(many, seed = 42).map { song -> song.id }

        rules.sortAndLimit(many.reversed(), seed = 42).map { song -> song.id } shouldBe first
        rules.sortAndLimit(many, seed = 43).map { song -> song.id } shouldNotBe first
        first shouldNotBe many.map { song -> song.id }
        first shouldContainExactlyInAnyOrder many.map { song -> song.id }
    }

    @Test
    fun `a song limit keeps the first songs after the sort`() {
        SmartRules(sort = SmartSort.PlayCount, descending = true, limit = Limit.Songs(2)).sortAndLimit(songs, seed = 0).map { song -> song.id } shouldBe listOf(2L, 1L)
        SmartRules(limit = Limit.Songs(10)).sortAndLimit(songs, seed = 0).size shouldBe 3
        SmartRules(limit = Limit.Songs(0)).sortAndLimit(songs, seed = 0) shouldBe emptyList()
    }

    @Test
    fun `a minute limit keeps the songs that fit in it`() {
        val fourMinutes = (1L..5L).map { id -> song(id = id, duration = 4 * 60_000) }

        SmartRules(limit = Limit.Minutes(12)).sortAndLimit(fourMinutes, seed = 0).size shouldBe 3
        SmartRules(limit = Limit.Minutes(15)).sortAndLimit(fourMinutes, seed = 0).size shouldBe 3
        SmartRules(limit = Limit.Minutes(3)).sortAndLimit(fourMinutes, seed = 0) shouldBe emptyList()
    }
}
