package com.simplecityapps.playback.lyrics

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

class LyricsCacheTest {
    private val cache = LyricsCache(InMemoryKeyValueStore(), Dispatchers.Default) { 1_000L }

    @Test
    fun `concurrent puts keep every entry within the bound`() = runTest {
        val total = LyricsCache.MAX_ENTRIES + 100L

        (1..total).map { id -> async(Dispatchers.Default) { cache.put(id, "text $id") } }.awaitAll()

        val kept = (1..total).count { cache.get(it) != null }
        kept shouldBe LyricsCache.MAX_ENTRIES
    }
}
