package com.simplecityapps.shuttle.query

import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ReleasedSinceQueryTest {
    private val query = SongQuery.ReleasedSince(
        clock = object : Clock {
            override fun now() = Instant.parse("2026-06-01T12:00:00Z")
        }
    )

    @Test
    fun `keeps songs released this year or last and not the year before`() {
        val thisYear = song(id = 1, date = LocalDate(2026, 1, 1))
        val lastYear = song(id = 2, date = LocalDate(2025, 12, 31))
        val yearBefore = song(id = 3, date = LocalDate(2024, 12, 31))
        val undated = song(id = 4, date = null)

        listOf(thisYear, lastYear, yearBefore, undated).filter(query.predicate) shouldContainExactly listOf(thisYear, lastYear)
    }
}
