package com.simplecityapps.shuttle.query

import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DownloadedQueryTest {
    @Test
    fun `keeps only the songs whose path has a completed download`() {
        val saved = song(id = 1, path = "remote://1")
        val other = song(id = 2, path = "remote://2")

        listOf(saved, other).filter(SongQuery.Downloaded(setOf("remote://1")).predicate) shouldContainExactly listOf(saved)
    }

    @Test
    fun `with no downloads nothing matches`() {
        listOf(song(id = 1)).filter(SongQuery.Downloaded().predicate) shouldBe emptyList()
    }

    @Test
    fun `the built-in playlist is addressable by its slug`() {
        SmartPlaylistId.entries.first { it.id == "downloaded" } shouldBe SmartPlaylistId.Downloaded
    }
}
