package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Pins the default rule on the checked-in sample of the song-match corpus (76 pairs per category), so a change to the
 * matcher shows up as a moved count here before it is re-scored on the full corpus.
 */
class SongCopyFixtureTest {
    private val pairs = parseSongCopyPairs(readSongCopyResource("song-copy/pairs.tsv"))
    private val matcher = SongCopyMatcher()

    private fun mergedByCategory(same: Boolean) = pairs.filter { it.same == same && !it.ambiguous }
        .groupBy { it.category }
        .mapValues { (_, group) -> group.count { matcher.match(it.a, it.b).isCopy } }
        .filterValues { it > 0 }

    @Test
    fun `the fixture holds every category`() {
        pairs.size shouldBe 1976
        pairs.groupBy { it.category }.values.map { it.size }.toSet() shouldBe setOf(76)
    }

    @Test
    fun `wrong merges are only clean and explicit editions and two near misses`() {
        mergedByCategory(same = false) shouldBe mapOf(
            "neg_clean_explicit" to 30,
            "neg_compilation_same_recording" to 1,
            "neg_version_marker" to 1
        )
    }

    @Test
    fun `copies of one song merge except where tags can't block them`() {
        mergedByCategory(same = true) shouldBe mapOf(
            "pos_edition_moved" to 1,
            "pos_edition_same_position" to 48,
            "pos_marker" to 56,
            "pos_multidisc" to 55,
            "pos_same_release" to 68,
            "pos_same_release_lb" to 62
        )
    }
}
