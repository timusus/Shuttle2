package com.simplecityapps.shuttle.playbackreporting

import android.content.Context
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PendingPlaysTest {
    private val sharedPreferences = RuntimeEnvironment.getApplication().getSharedPreferences("pending_plays_test", Context.MODE_PRIVATE)

    private fun play(songId: Long) = PendingPlays.Play(songId, Instant.fromEpochMilliseconds(1_700_000_000_000 + songId))

    @Test
    fun `plays survive a new instance`() {
        PendingPlays(sharedPreferences).add(play(1))
        PendingPlays(sharedPreferences).add(play(2))

        PendingPlays(sharedPreferences).all() shouldBe listOf(play(1), play(2))
    }

    @Test
    fun `the oldest plays are dropped past the cap`() {
        val pendingPlays = PendingPlays(sharedPreferences)

        (1L..PendingPlays.CAPACITY + 5L).forEach { songId -> pendingPlays.add(play(songId)) }

        pendingPlays.all() shouldBe (6L..PendingPlays.CAPACITY + 5L).map { songId -> play(songId) }
    }

    @Test
    fun `removing plays keeps the rest`() {
        val pendingPlays = PendingPlays(sharedPreferences)
        (1L..3L).forEach { songId -> pendingPlays.add(play(songId)) }

        pendingPlays.remove(listOf(play(1), play(3)))
        pendingPlays.all() shouldBe listOf(play(2))

        pendingPlays.remove(listOf(play(2)))
        pendingPlays.all().shouldBeEmpty()
    }
}
