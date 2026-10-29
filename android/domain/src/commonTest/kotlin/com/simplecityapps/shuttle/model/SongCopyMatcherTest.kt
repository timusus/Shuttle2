package com.simplecityapps.shuttle.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SongCopyMatcherTest {
    private val matcher = SongCopyMatcher()

    private fun tags(
        title: String? = "Paranoid Android",
        album: String? = "OK Computer",
        albumArtist: String? = "Radiohead",
        artist: String? = "Radiohead",
        disc: Int? = 1,
        track: Int? = 2,
        durationMs: Int? = 383_000,
        year: Int? = 1997
    ) = SongCopyTags(title, album, albumArtist, artist, disc, track, durationMs, year)

    private fun merges(
        a: SongCopyTags,
        b: SongCopyTags
    ) = matcher.match(a, b).isCopy

    @Test
    fun `a server copy with remaster qualifiers and a missing disc merges with the local file`() {
        merges(
            tags(),
            tags(title = "Paranoid Android (2017 Remaster)", album = "OK Computer [OKNOTOK Edition]", disc = null, durationMs = 384_400, year = 2017)
        ) shouldBe true
    }

    @Test
    fun `case diacritics featuring credits and ampersands don't keep copies apart`() {
        merges(
            tags(title = "Café Del Mar (feat. Someone)", album = "Beats & Pieces"),
            tags(title = "cafe del mar", album = "Beats and Pieces")
        ) shouldBe true
    }

    @Test
    fun `durations beyond the tolerance are different recordings`() {
        merges(tags(), tags(durationMs = 386_000)) shouldBe false
    }

    @Test
    fun `a missing duration is no evidence either way`() {
        merges(tags(), tags(durationMs = null)) shouldBe true
    }

    @Test
    fun `a live or remixed version never merges with the studio one`() {
        merges(tags(), tags(title = "Paranoid Android (Live)")) shouldBe false
        merges(tags(), tags(title = "Paranoid Android - Remix")) shouldBe false
    }

    @Test
    fun `explicit and clean versions stay apart`() {
        merges(tags(title = "Song (Explicit)"), tags(title = "Song (Clean)")) shouldBe false
    }

    @Test
    fun `another track or disc of the album is a different song`() {
        merges(tags(), tags(track = 3)) shouldBe false
        merges(tags(), tags(disc = 2)) shouldBe false
    }

    @Test
    fun `untagged album or track never merges`() {
        merges(tags(album = null), tags(album = null)) shouldBe false
        merges(tags(track = null), tags(track = null)) shouldBe false
    }

    @Test
    fun `a placeholder album reads as untagged`() {
        merges(tags(album = "Unknown Album"), tags(album = "<unknown>")) shouldBe false
    }

    @Test
    fun `a qualifier that names no edition keeps titles apart`() {
        merges(tags(), tags(title = "Paranoid Android (take 8)")) shouldBe false
    }

    @Test
    fun `the blocking hash is stable across copies`() {
        matcher.prepare(tags(album = "The OK Computer")).blockingHash shouldBe matcher.prepare(tags(album = "ok computer", disc = null)).blockingHash
    }
}
