package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test

class PendingPlaysTest {
    private val store = InMemoryKeyValueStore()

    private fun play(songId: Long) = PendingPlays.Play(songId, Instant.fromEpochMilliseconds(1_700_000_000_000 + songId))

    @Test
    fun `plays survive a new instance`() {
        PendingPlays(store).add(play(1))
        PendingPlays(store).add(play(2))

        PendingPlays(store).all() shouldBe listOf(play(1), play(2))
    }

    @Test
    fun `plays are saved under their key as songId colon epoch millis, comma separated`() {
        PendingPlays(store).add(play(1))
        PendingPlays(store).add(play(2))

        store.values shouldBe mapOf("playback_report_pending_plays" to "1:1700000000001,2:1700000000002")
    }

    @Test
    fun `plays saved by an older build read back`() {
        val saved = InMemoryKeyValueStore(mapOf("playback_report_pending_plays" to "7:1700000000007,garbage,8:1700000000008"))

        PendingPlays(saved).all() shouldBe listOf(play(7), play(8))
    }

    @Test
    fun `the oldest plays are dropped past the cap`() {
        val pendingPlays = PendingPlays(store)

        (1L..PendingPlays.CAPACITY + 5L).forEach { songId -> pendingPlays.add(play(songId)) }

        pendingPlays.all() shouldBe (6L..PendingPlays.CAPACITY + 5L).map { songId -> play(songId) }
    }

    @Test
    fun `removing plays keeps the rest, and removing the last removes the key`() {
        val pendingPlays = PendingPlays(store)
        (1L..3L).forEach { songId -> pendingPlays.add(play(songId)) }

        pendingPlays.remove(listOf(play(1), play(3)))
        pendingPlays.all() shouldBe listOf(play(2))

        pendingPlays.remove(listOf(play(2)))
        pendingPlays.all().shouldBeEmpty()
        store.contains("playback_report_pending_plays") shouldBe false
    }
}
